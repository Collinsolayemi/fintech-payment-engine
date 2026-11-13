package com.fintech.engine.service.impl;

import com.fintech.engine.dto.*;
import com.fintech.engine.exception.AccountNotFoundException;
import com.fintech.engine.exception.InvalidAmountException;
import com.fintech.engine.exception.UnauthorizedAccountAccessException;
import com.fintech.engine.exception.UserNotFoundException;
import com.fintech.engine.model.entity.Account;
import com.fintech.engine.model.entity.User;
import com.fintech.engine.model.entity.VirtualAccount;
import com.fintech.engine.model.enums.AccountStatus;
import com.fintech.engine.model.enums.AccountType;
import com.fintech.engine.model.enums.EntryType;
import com.fintech.engine.model.enums.VirtualAccountStatus;
import com.fintech.engine.repository.AccountRepository;
import com.fintech.engine.repository.UserRepository;
import com.fintech.engine.repository.VirtualAccountRepository;
import com.fintech.engine.service.AccountService;
import com.fintech.engine.service.LedgerService;
import com.fintech.engine.service.VirtualAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class VirtualAccountServiceImpl implements VirtualAccountService {

    private static final Logger log = LoggerFactory.getLogger(VirtualAccountServiceImpl.class);
    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "GBP", "EUR", "NGN", "CAD");

    private final VirtualAccountRepository virtualAccountRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final SecureRandom secureRandom = new SecureRandom();

    public VirtualAccountServiceImpl(
            VirtualAccountRepository virtualAccountRepository,
            UserRepository userRepository,
            AccountRepository accountRepository,
            AccountService accountService,
            LedgerService ledgerService
    ) {
        this.virtualAccountRepository = virtualAccountRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.accountService = accountService;
        this.ledgerService = ledgerService;
    }

    @Override
    public VirtualAccountResponse createVirtualAccount(String userEmail, CreateVirtualAccountRequest request) {
        User user = resolveUser(userEmail);
        String currency = request.currency().trim().toUpperCase(Locale.ROOT);

        if (!SUPPORTED_CURRENCIES.contains(currency)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported virtual account currency: " + currency + ". Supported: " + SUPPORTED_CURRENCIES
            );
        }

        if (virtualAccountRepository.existsByUserIdAndCurrency(user.getId(), currency)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You already have a " + currency + " virtual account"
            );
        }

        Account linkedWallet = resolveOrProvisionWallet(user, currency, request.accountId());
        BankCoordinates coords = generateBankCoordinates(currency);
        String accountName = (user.getFirstName() + " " + user.getLastName()).trim().toUpperCase(Locale.ROOT);

        VirtualAccount virtualAccount = new VirtualAccount(
                user,
                linkedWallet,
                currency,
                coords.accountNumber(),
                accountName,
                coords.bankName(),
                coords.routingNumber(),
                coords.sortCode(),
                coords.iban(),
                coords.bicSwift(),
                VirtualAccountStatus.ACTIVE,
                "SIMULATED"
        );

        try {
            VirtualAccount saved = virtualAccountRepository.saveAndFlush(virtualAccount);
            log.info("Provisioned {} virtual account {} for user {}", currency, saved.getAccountNumber(), user.getEmail());
            return VirtualAccountResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Virtual account already exists for currency " + currency);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<VirtualAccountResponse> getUserVirtualAccounts(String userEmail) {
        User user = resolveUser(userEmail);
        return virtualAccountRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(VirtualAccountResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public VirtualAccountResponse getVirtualAccount(String userEmail, UUID virtualAccountId) {
        VirtualAccount va = resolveOwnedVirtualAccount(userEmail, virtualAccountId);
        return VirtualAccountResponse.from(va);
    }

    @Override
    public VirtualAccountResponse updateStatus(String userEmail, UUID virtualAccountId, VirtualAccountStatus status) {
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        }
        VirtualAccount va = resolveOwnedVirtualAccount(userEmail, virtualAccountId);

        if (va.getStatus() == VirtualAccountStatus.CLOSED && status != VirtualAccountStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Closed virtual accounts cannot be reopened");
        }

        va.setStatus(status);
        VirtualAccount updated = virtualAccountRepository.save(va);
        log.info("Updated virtual account {} status to {}", updated.getAccountNumber(), status);
        return VirtualAccountResponse.from(updated);
    }

    @Override
    public VirtualAccountDepositResponse simulateInboundDeposit(
            String userEmail,
            UUID virtualAccountId,
            SimulateVirtualAccountDepositRequest request,
            String idempotencyKey
    ) {
        VirtualAccount va = resolveOwnedVirtualAccount(userEmail, virtualAccountId);

        if (va.getStatus() != VirtualAccountStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Virtual account must be ACTIVE to receive deposits (current: " + va.getStatus() + ")"
            );
        }

        Account wallet = va.getAccount();
        if (wallet.getStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Linked wallet is not ACTIVE");
        }

        BigDecimal amount = request.amount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException(amount);
        }

        String currency = va.getCurrency();
        Account clearingAccount = accountRepository.findByAccountNumber("SYS-1002-" + currency)
                .orElseGet(() -> accountRepository.findByAccountNumber("SYS-1001-" + currency)
                        .orElseThrow(() -> new AccountNotFoundException("Platform clearing account not found for currency: " + currency)));

        String senderName = (request.senderName() != null && !request.senderName().isBlank())
                ? request.senderName().trim()
                : "External Bank Sender";
        String narration = (request.narration() != null && !request.narration().isBlank())
                ? request.narration().trim()
                : "Inbound bank transfer to " + va.getAccountNumber();

        String reference = "VA-DEP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String effectiveIdempotencyKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey.trim()
                : "va-dep-" + UUID.randomUUID();

        LedgerPostingRequest postingRequest = new LedgerPostingRequest(
                reference,
                effectiveIdempotencyKey,
                narration + " (From: " + senderName + ")",
                List.of(
                        new LedgerEntryCommand(clearingAccount.getId(), EntryType.DEBIT, amount),
                        new LedgerEntryCommand(wallet.getId(), EntryType.CREDIT, amount)
                )
        );

        ledgerService.postTransaction(postingRequest);
        AccountBalanceResponse updatedBalance = ledgerService.getAccountBalance(wallet.getId());

        log.info("Credited {} {} to wallet {} via virtual account {} (ref: {})",
                amount, currency, wallet.getAccountNumber(), va.getAccountNumber(), reference);

        return new VirtualAccountDepositResponse(
                reference,
                va.getId(),
                wallet.getId(),
                va.getAccountNumber(),
                currency,
                amount,
                updatedBalance.balance(),
                senderName,
                narration,
                OffsetDateTime.now()
        );
    }

    private User resolveUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));
    }

    private VirtualAccount resolveOwnedVirtualAccount(String userEmail, UUID virtualAccountId) {
        User user = resolveUser(userEmail);
        VirtualAccount va = virtualAccountRepository.findById(virtualAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Virtual account not found"));

        if (!va.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedAccountAccessException(va.getAccount().getId(), userEmail);
        }
        return va;
    }

    private Account resolveOrProvisionWallet(User user, String currency, UUID requestedAccountId) {
        if (requestedAccountId != null) {
            Account account = accountRepository.findById(requestedAccountId)
                    .orElseThrow(() -> new AccountNotFoundException(requestedAccountId));
            if (account.getUser() == null || !account.getUser().getId().equals(user.getId())) {
                throw new UnauthorizedAccountAccessException(requestedAccountId, user.getEmail());
            }
            if (account.getType() != AccountType.LIABILITY || account.getStatus() != AccountStatus.ACTIVE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target account must be an ACTIVE customer wallet");
            }
            if (!currency.equalsIgnoreCase(account.getCurrency())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target wallet currency does not match " + currency);
            }
            return account;
        }

        return accountRepository.findByUserId(user.getId()).stream()
                .filter(a -> a.getType() == AccountType.LIABILITY && a.getStatus() == AccountStatus.ACTIVE)
                .filter(a -> currency.equalsIgnoreCase(a.getCurrency()))
                .findFirst()
                .orElseGet(() -> {
                    AccountBalanceResponse created = accountService.createCurrencyWallet(
                            user.getEmail(),
                            new CreateCurrencyWalletRequest(currency, null)
                    );
                    return accountRepository.findById(created.accountId())
                            .orElseThrow(() -> new AccountNotFoundException(created.accountId()));
                });
    }

    private BankCoordinates generateBankCoordinates(String currency) {
        String accountNumber = generateUniqueAccountNumber(currency);
        return switch (currency) {
            case "USD" -> new BankCoordinates(
                    accountNumber,
                    "Community Federal Savings Bank",
                    "026073150",
                    null,
                    null,
                    "CFSBUS33"
            );
            case "GBP" -> new BankCoordinates(
                    accountNumber,
                    "ClearBank UK",
                    null,
                    "04-00-75",
                    "GB29CLRB040075" + accountNumber,
                    "CLRBGB22"
            );
            case "EUR" -> new BankCoordinates(
                    accountNumber,
                    "Banking Circle S.A.",
                    null,
                    null,
                    "DE8937040044" + accountNumber,
                    "BCIRLULL"
            );
            case "NGN" -> new BankCoordinates(
                    accountNumber,
                    "Providus Bank",
                    null,
                    null,
                    null,
                    "PROVNGLA"
            );
            case "CAD" -> new BankCoordinates(
                    accountNumber,
                    "Peoples Trust Company",
                    "0621-00012",
                    null,
                    null,
                    "PTCOCATT"
            );
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported currency: " + currency);
        };
    }

    private String generateUniqueAccountNumber(String currency) {
        for (int attempt = 0; attempt < 20; attempt++) {
            String candidate = switch (currency) {
                case "GBP" -> String.format("%08d", 10000000 + secureRandom.nextInt(90000000));
                case "NGN" -> "99" + String.format("%08d", secureRandom.nextInt(100000000));
                case "CAD" -> "88" + String.format("%010d", Math.abs(secureRandom.nextLong() % 10_000_000_000L));
                default -> "40" + String.format("%08d", secureRandom.nextInt(100000000));
            };
            if (!virtualAccountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate unique virtual account number");
    }

    private record BankCoordinates(
            String accountNumber,
            String bankName,
            String routingNumber,
            String sortCode,
            String iban,
            String bicSwift
    ) {
    }
}

