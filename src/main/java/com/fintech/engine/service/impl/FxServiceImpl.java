package com.fintech.engine.service.impl;

import com.fintech.engine.dto.*;
import com.fintech.engine.exception.*;
import com.fintech.engine.model.entity.Account;
import com.fintech.engine.model.entity.FxQuote;
import com.fintech.engine.model.entity.User;
import com.fintech.engine.model.enums.AccountStatus;
import com.fintech.engine.model.enums.AccountType;
import com.fintech.engine.model.enums.EntryType;
import com.fintech.engine.model.enums.FxQuoteStatus;
import com.fintech.engine.repository.AccountRepository;
import com.fintech.engine.repository.FxQuoteRepository;
import com.fintech.engine.repository.UserRepository;
import com.fintech.engine.service.FxService;
import com.fintech.engine.service.LedgerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class FxServiceImpl implements FxService {

    private static final Logger log = LoggerFactory.getLogger(FxServiceImpl.class);

    private static final Map<String, BigDecimal> BASE_USD_RATES = new LinkedHashMap<>();
    static {
        BASE_USD_RATES.put("USD", new BigDecimal("1.000000"));
        BASE_USD_RATES.put("EUR", new BigDecimal("0.925000"));
        BASE_USD_RATES.put("GBP", new BigDecimal("0.792000"));
        BASE_USD_RATES.put("CAD", new BigDecimal("1.385000"));
        BASE_USD_RATES.put("AUD", new BigDecimal("1.520000"));
        BASE_USD_RATES.put("JPY", new BigDecimal("154.500000"));
        BASE_USD_RATES.put("NGN", new BigDecimal("1650.000000"));
    }

    private static final BigDecimal SPREAD_MARGIN = new BigDecimal("0.0035"); // 0.35%
    private static final BigDecimal BASE_FEE_PERCENT = new BigDecimal("0.0010"); // 0.10%
    private static final BigDecimal MIN_FEE = new BigDecimal("0.50");

    private final FxQuoteRepository fxQuoteRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final LedgerService ledgerService;
    private final SecureRandom secureRandom = new SecureRandom();

    public FxServiceImpl(
            FxQuoteRepository fxQuoteRepository,
            AccountRepository accountRepository,
            UserRepository userRepository,
            LedgerService ledgerService) {
        this.fxQuoteRepository = fxQuoteRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.ledgerService = ledgerService;
    }

    @Override
    public List<FxRateResponse> getLiveRates(String baseCurrency) {
        String base = (baseCurrency != null && !baseCurrency.isBlank())
                ? baseCurrency.toUpperCase().trim()
                : "USD";

        if (!BASE_USD_RATES.containsKey(base)) {
            throw new IllegalArgumentException("Unsupported base currency: " + base);
        }

        BigDecimal baseToUsdRate = BASE_USD_RATES.get(base);
        OffsetDateTime now = OffsetDateTime.now();
        List<FxRateResponse> rates = new ArrayList<>();

        for (Map.Entry<String, BigDecimal> entry : BASE_USD_RATES.entrySet()) {
            String target = entry.getKey();
            if (target.equalsIgnoreCase(base)) continue;

            BigDecimal targetToUsdRate = entry.getValue();
            // rate = targetRate / baseRate
            BigDecimal rate = targetToUsdRate.divide(baseToUsdRate, 6, RoundingMode.HALF_UP);
            BigDecimal inverseRate = baseToUsdRate.divide(targetToUsdRate, 6, RoundingMode.HALF_UP);

            rates.add(new FxRateResponse(base, target, rate, inverseRate, now));
        }

        return rates;
    }

    @Override
    @Transactional
    public FxQuoteResponse createQuote(String userEmailOrId, FxQuoteRequest request) {
        User user = resolveUser(userEmailOrId);

        String sourceCurr = request.sourceCurrency().toUpperCase().trim();
        String targetCurr = request.targetCurrency().toUpperCase().trim();

        if (sourceCurr.equalsIgnoreCase(targetCurr)) {
            throw new IllegalArgumentException("Source and target currencies must be distinct");
        }

        if (!BASE_USD_RATES.containsKey(sourceCurr) || !BASE_USD_RATES.containsKey(targetCurr)) {
            throw new IllegalArgumentException("Unsupported currency pair: " + sourceCurr + " -> " + targetCurr);
        }

        BigDecimal sourceAmount = request.sourceAmount();
        if (sourceAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException(sourceAmount);
        }

        BigDecimal sourceBaseRate = BASE_USD_RATES.get(sourceCurr);
        BigDecimal targetBaseRate = BASE_USD_RATES.get(targetCurr);

        // Raw mid-market rate
        BigDecimal rawRate = targetBaseRate.divide(sourceBaseRate, 6, RoundingMode.HALF_UP);
        // Apply institutional spread (0.35%)
        BigDecimal customerRate = rawRate.multiply(BigDecimal.ONE.subtract(SPREAD_MARGIN))
                .setScale(6, RoundingMode.HALF_UP);

        // Platform fee: 0.10% or minimum 0.50
        BigDecimal calculatedFee = sourceAmount.multiply(BASE_FEE_PERCENT).setScale(4, RoundingMode.HALF_UP);
        BigDecimal fee = calculatedFee.compareTo(MIN_FEE) < 0 ? MIN_FEE : calculatedFee;

        // Target amount = sourceAmount * customerRate
        BigDecimal targetAmount = sourceAmount.multiply(customerRate).setScale(4, RoundingMode.HALF_UP);

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plusSeconds(60); // 60s guaranteed rate lock

        FxQuote quote = new FxQuote();
        quote.setUser(user);
        quote.setSourceCurrency(sourceCurr);
        quote.setTargetCurrency(targetCurr);
        quote.setSourceAmount(sourceAmount);
        quote.setTargetAmount(targetAmount);
        quote.setExchangeRate(customerRate);
        quote.setFee(fee);
        quote.setStatus(FxQuoteStatus.PENDING);
        quote.setExpiresAt(expiresAt);
        quote = fxQuoteRepository.save(quote);

        log.info("Created FX Quote {}: {} {} -> {} {} at rate {}",
                quote.getId(), sourceAmount, sourceCurr, targetAmount, targetCurr, customerRate);

        return new FxQuoteResponse(
                quote.getId(),
                sourceCurr,
                targetCurr,
                sourceAmount,
                targetAmount,
                customerRate,
                fee,
                60L,
                expiresAt
        );
    }

    @Override
    @Transactional
    public FxExchangeResponse executeQuote(String userEmailOrId, ExecuteFxExchangeRequest request, String idempotencyKey) {
        User user = resolveUser(userEmailOrId);

        FxQuote quote = fxQuoteRepository.findByIdAndUserId(request.quoteId(), user.getId())
                .orElseThrow(() -> new FxQuoteNotFoundException(request.quoteId()));

        if (quote.getStatus() == FxQuoteStatus.EXECUTED) {
            throw new IllegalStateException("FX Quote " + request.quoteId() + " has already been executed");
        }

        if (OffsetDateTime.now().isAfter(quote.getExpiresAt())) {
            quote.setStatus(FxQuoteStatus.EXPIRED);
            fxQuoteRepository.save(quote);
            throw new FxQuoteExpiredException(request.quoteId());
        }

        Account sourceAccount = accountRepository.findById(request.sourceAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Source account not found: " + request.sourceAccountId()));
        Account targetAccount = accountRepository.findById(request.targetAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Target account not found: " + request.targetAccountId()));

        // Verification checks
        if (!sourceAccount.getUser().getId().equals(user.getId()) || !targetAccount.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedAccountAccessException(sourceAccount.getId(), user.getEmail());
        }

        if (sourceAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Source account " + sourceAccount.getAccountNumber() + " is " + sourceAccount.getStatus());
        }
        if (targetAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Target account " + targetAccount.getAccountNumber() + " is " + targetAccount.getStatus());
        }

        if (!sourceAccount.getCurrency().equalsIgnoreCase(quote.getSourceCurrency())) {
            throw new CurrencyMismatchException(sourceAccount.getCurrency(), quote.getSourceCurrency());
        }
        if (!targetAccount.getCurrency().equalsIgnoreCase(quote.getTargetCurrency())) {
            throw new CurrencyMismatchException(targetAccount.getCurrency(), quote.getTargetCurrency());
        }

        // Available balance check: sourceAmount + fee
        BigDecimal totalDebit = quote.getSourceAmount().add(quote.getFee());
        BigDecimal availableBalance = ledgerService.getAccountBalance(sourceAccount.getId()).balance();
        if (availableBalance.compareTo(totalDebit) < 0) {
            throw new InsufficientFundsException(sourceAccount.getId(), totalDebit, availableBalance);
        }

        // Locate clearing & revenue accounts
        Account sourceClearing = findOrCreateClearingAccount(quote.getSourceCurrency());
        Account targetClearing = findOrCreateClearingAccount(quote.getTargetCurrency());
        Account feeRevenue = findOrCreateFeeAccount(quote.getSourceCurrency());

        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int rand = secureRandom.nextInt(900000) + 100000;
        String txRef = "FX-" + date + "-" + rand;

        // Leg 1: Debit user source wallet (amount + fee), Credit platform source clearing & revenue
        List<LedgerEntryCommand> leg1Entries = new ArrayList<>();
        leg1Entries.add(new LedgerEntryCommand(sourceAccount.getId(), EntryType.DEBIT, totalDebit));
        leg1Entries.add(new LedgerEntryCommand(sourceClearing.getId(), EntryType.CREDIT, quote.getSourceAmount()));
        if (quote.getFee().compareTo(BigDecimal.ZERO) > 0) {
            leg1Entries.add(new LedgerEntryCommand(feeRevenue.getId(), EntryType.CREDIT, quote.getFee()));
        }

        LedgerPostingRequest leg1 = new LedgerPostingRequest(
                txRef + "-LEG1",
                null,
                "FX Outbound: Converted " + quote.getSourceAmount() + " " + quote.getSourceCurrency(),
                leg1Entries
        );
        ledgerService.postTransaction(leg1);

        // Leg 2: Debit platform target clearing, Credit user target wallet
        List<LedgerEntryCommand> leg2Entries = List.of(
                new LedgerEntryCommand(targetClearing.getId(), EntryType.DEBIT, quote.getTargetAmount()),
                new LedgerEntryCommand(targetAccount.getId(), EntryType.CREDIT, quote.getTargetAmount())
        );

        LedgerPostingRequest leg2 = new LedgerPostingRequest(
                txRef + "-LEG2",
                null,
                "FX Inbound: Received " + quote.getTargetAmount() + " " + quote.getTargetCurrency(),
                leg2Entries
        );
        ledgerService.postTransaction(leg2);

        quote.setStatus(FxQuoteStatus.EXECUTED);
        fxQuoteRepository.save(quote);

        log.info("Executed FX Exchange {}: {} {} -> {} {} for user {}",
                txRef, quote.getSourceAmount(), quote.getSourceCurrency(),
                quote.getTargetAmount(), quote.getTargetCurrency(), user.getEmail());

        return new FxExchangeResponse(
                txRef,
                sourceAccount.getId(),
                targetAccount.getId(),
                quote.getSourceAmount(),
                quote.getTargetAmount(),
                quote.getSourceCurrency(),
                quote.getTargetCurrency(),
                quote.getExchangeRate(),
                quote.getFee(),
                OffsetDateTime.now()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<FxQuoteResponse> getUserQuotes(String userEmailOrId) {
        User user = resolveUser(userEmailOrId);
        return fxQuoteRepository.findTop20ByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(q -> new FxQuoteResponse(
                        q.getId(),
                        q.getSourceCurrency(),
                        q.getTargetCurrency(),
                        q.getSourceAmount(),
                        q.getTargetAmount(),
                        q.getExchangeRate(),
                        q.getFee(),
                        Math.max(0, q.getExpiresAt().toEpochSecond() - OffsetDateTime.now().toEpochSecond()),
                        q.getExpiresAt()
                ))
                .toList();
    }

    private User resolveUser(String userEmailOrId) {
        try {
            UUID userId = UUID.fromString(userEmailOrId);
            return userRepository.findById(userId)
                    .orElseGet(() -> userRepository.findByEmail(userEmailOrId)
                            .orElseThrow(() -> new UserNotFoundException(userEmailOrId)));
        } catch (IllegalArgumentException notUuid) {
            return userRepository.findByEmail(userEmailOrId)
                    .orElseThrow(() -> new UserNotFoundException(userEmailOrId));
        }
    }

    private Account findOrCreateClearingAccount(String currency) {
        String fxAccNo = "SYS-FX-" + currency.toUpperCase();
        return accountRepository.findByAccountNumber(fxAccNo)
                .orElseGet(() -> accountRepository.findByAccountNumber("SYS-1001-" + currency.toUpperCase())
                        .orElseGet(() -> {
                            Account acc = new Account(null, fxAccNo, "FX_CLEARING_" + currency, AccountType.ASSET, currency);
                            return accountRepository.save(acc);
                        }));
    }

    private Account findOrCreateFeeAccount(String currency) {
        String feeAccNo = "SYS-4001-" + currency.toUpperCase();
        return accountRepository.findByAccountNumber(feeAccNo)
                .orElseGet(() -> accountRepository.findByAccountNumber("SYS-4001-USD")
                        .orElseGet(() -> {
                            Account acc = new Account(null, feeAccNo, "REVENUE_FX_FEES_" + currency, AccountType.REVENUE, currency);
                            return accountRepository.save(acc);
                        }));
    }
}

