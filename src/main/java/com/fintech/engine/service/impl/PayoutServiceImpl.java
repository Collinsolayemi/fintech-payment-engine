package com.fintech.engine.service.impl;

import com.fintech.engine.dto.InitiatePayoutRequest;
import com.fintech.engine.dto.LedgerEntryCommand;
import com.fintech.engine.dto.LedgerPostingRequest;
import com.fintech.engine.dto.PayoutResponse;
import com.fintech.engine.exception.*;
import com.fintech.engine.model.entity.Account;
import com.fintech.engine.model.entity.Payout;
import com.fintech.engine.model.entity.User;
import com.fintech.engine.model.enums.EntryType;
import com.fintech.engine.model.enums.PayoutStatus;
import com.fintech.engine.repository.AccountRepository;
import com.fintech.engine.repository.PayoutRepository;
import com.fintech.engine.repository.UserRepository;
import com.fintech.engine.service.LedgerService;
import com.fintech.engine.service.PayoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PayoutServiceImpl implements PayoutService {

    private static final Logger log = LoggerFactory.getLogger(PayoutServiceImpl.class);

    private final PayoutRepository payoutRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final LedgerService ledgerService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${fintech.payouts.flat-fee:2.00}")
    private BigDecimal flatPayoutFee = new BigDecimal("2.00");

    public PayoutServiceImpl(
        PayoutRepository payoutRepository,
        AccountRepository accountRepository,
        UserRepository userRepository,
        LedgerService ledgerService
    ) {
        this.payoutRepository = payoutRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.ledgerService = ledgerService;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PayoutResponse initiatePayout(InitiatePayoutRequest request, String idempotencyKey, String authenticatedEmail) {
        log.info("Processing outbound payout initiation for account: {}, amount: {} {}",
                request.accountId(), request.amount(), request.currency());

        String currency = request.currency().toUpperCase();

        // 1. Resolve & Validate User Ownership
        Account sourceAccount = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new AccountNotFoundException(request.accountId()));

        if (authenticatedEmail != null) {
            User authUser = userRepository.findByEmail(authenticatedEmail)
                    .orElseThrow(() -> new UserNotFoundException("Authenticated user not found"));

            if (sourceAccount.getUser() == null || !sourceAccount.getUser().getId().equals(authUser.getId())) {
                log.warn("Security violation: User {} does not own account {}", authenticatedEmail, request.accountId());
                throw new UnauthorizedAccountAccessException(request.accountId(), authenticatedEmail);
            }
        }

        if (!sourceAccount.getCurrency().equalsIgnoreCase(currency)) {
            throw new CurrencyMismatchException(sourceAccount.getCurrency(), currency);
        }

        // 2. Fetch platform accounts for Escrow and Fee
        String escrowAccountNumber = "SYS-2002-" + currency;
        Account escrowAccount = accountRepository.findByAccountNumber(escrowAccountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Escrow account not configured for: " + currency));

        String feeAccountNumber = "SYS-4001-" + currency;
        Account feeAccount = accountRepository.findByAccountNumber(feeAccountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Fee account not configured for: " + currency));

        BigDecimal fee = flatPayoutFee;
        BigDecimal totalRequired = request.amount().add(fee);

        // 3. Acquire Deterministic Pessimistic Locks (Deadlock Prevention)
        List<UUID> lockIds = new ArrayList<>(List.of(sourceAccount.getId(), escrowAccount.getId(), feeAccount.getId()));
        lockIds.sort(UUID::compareTo);
        for (UUID lockId : lockIds) {
            accountRepository.findByIdForUpdate(lockId);
        }

        // 4. Balance check
        BigDecimal availableBalance = ledgerService.getAccountBalance(sourceAccount.getId()).balance();
        if (availableBalance.compareTo(totalRequired) < 0) {
            throw new InsufficientFundsException(sourceAccount.getId(), availableBalance, totalRequired);
        }

        // 5. Phase 1: Two-Phase Escrow Lock (Balanced Journal Entry)
        String payoutRef = generatePayoutReference();
        String escrowJournalRef = "ESCROW-" + payoutRef;

        List<LedgerEntryCommand> commands = new ArrayList<>();
        // Debit: User Wallet for Principal + Fee
        commands.add(new LedgerEntryCommand(sourceAccount.getId(), EntryType.DEBIT, totalRequired));
        // Credit: Pending Settlements Escrow for Principal
        commands.add(new LedgerEntryCommand(escrowAccount.getId(), EntryType.CREDIT, request.amount()));
        // Credit: Platform Fee Revenue
        commands.add(new LedgerEntryCommand(feeAccount.getId(), EntryType.CREDIT, fee));

        LedgerPostingRequest ledgerRequest = new LedgerPostingRequest(
                escrowJournalRef,
                "Outbound Payout Escrow Lock: " + payoutRef,
                payoutRef,
                commands
        );
        ledgerService.postTransaction(ledgerRequest);

        // 6. Record Payout with PENDING_ESCROW / PROCESSING
        Payout payout = new Payout(
                payoutRef,
                sourceAccount.getUser(),
                sourceAccount,
                request.amount(),
                fee,
                currency,
                request.destinationBankCode(),
                request.destinationAccountNumber(),
                request.destinationAccountName(),
                idempotencyKey
        );
        payout.markProcessing();
        payout = payoutRepository.save(payout);

        log.info("Payout {} successfully initiated and held in escrow", payoutRef);
        return mapToResponse(payout);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PayoutResponse completePayout(String reference) {
        log.info("Processing external settlement completion for payout: {}", reference);

        Payout payout = payoutRepository.findByReference(reference)
                .orElseThrow(() -> new PayoutNotFoundException(reference));

        if (payout.getStatus() == PayoutStatus.COMPLETED) {
            log.info("Payout {} already completed", reference);
            return mapToResponse(payout);
        }

        if (payout.getStatus() != PayoutStatus.PROCESSING && payout.getStatus() != PayoutStatus.PENDING_ESCROW) {
            throw new IllegalPayoutStateException("Cannot complete payout in status: " + payout.getStatus());
        }

        String currency = payout.getCurrency();
        Account escrowAccount = accountRepository.findByAccountNumber("SYS-2002-" + currency)
                .orElseThrow(() -> new AccountNotFoundException("Escrow account not found for: " + currency));

        Account bankClearingAccount = accountRepository.findByAccountNumber("SYS-1001-" + currency)
                .orElseThrow(() -> new AccountNotFoundException("Bank clearing account not found for: " + currency));

        // Deterministic Lock
        List<UUID> lockIds = new ArrayList<>(List.of(escrowAccount.getId(), bankClearingAccount.getId()));
        lockIds.sort(UUID::compareTo);
        for (UUID lockId : lockIds) {
            accountRepository.findByIdForUpdate(lockId);
        }

        // Phase 2A: Complete Outbound Settlement
        String settlementRef = "SETTLE-" + reference;
        LedgerPostingRequest settleRequest = new LedgerPostingRequest(
                settlementRef,
                "Outbound Payout Bank Settlement: " + reference,
                settlementRef,
                List.of(
                        new LedgerEntryCommand(escrowAccount.getId(), EntryType.DEBIT, payout.getAmount()),
                        new LedgerEntryCommand(bankClearingAccount.getId(), EntryType.CREDIT, payout.getAmount())
                )
        );
        ledgerService.postTransaction(settleRequest);

        payout.markCompleted();
        payout = payoutRepository.save(payout);

        log.info("Payout {} settled successfully with clearing bank", reference);
        return mapToResponse(payout);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PayoutResponse reversePayout(String reference, String failureReason) {
        log.info("Reversing failed payout {}: {}", reference, failureReason);

        Payout payout = payoutRepository.findByReference(reference)
                .orElseThrow(() -> new PayoutNotFoundException(reference));

        if (payout.getStatus() == PayoutStatus.REVERSED) {
            log.info("Payout {} already reversed", reference);
            return mapToResponse(payout);
        }

        if (payout.getStatus() != PayoutStatus.PROCESSING && payout.getStatus() != PayoutStatus.PENDING_ESCROW) {
            throw new IllegalPayoutStateException("Cannot reverse payout in status: " + payout.getStatus());
        }

        String currency = payout.getCurrency();
        Account escrowAccount = accountRepository.findByAccountNumber("SYS-2002-" + currency)
                .orElseThrow(() -> new AccountNotFoundException("Escrow account not found for: " + currency));
        Account userAccount = payout.getAccount();
        Account feeAccount = accountRepository.findByAccountNumber("SYS-4001-" + currency)
                .orElseThrow(() -> new AccountNotFoundException("Fee account not found for: " + currency));

        // Deterministic Lock
        List<UUID> lockIds = new ArrayList<>(List.of(escrowAccount.getId(), userAccount.getId(), feeAccount.getId()));
        lockIds.sort(UUID::compareTo);
        for (UUID lockId : lockIds) {
            accountRepository.findByIdForUpdate(lockId);
        }

        // Phase 2B: Reverse Escrow & Refund User
        BigDecimal refundAmount = payout.getAmount().add(payout.getFee());
        String reversalRef = "REV-" + reference;

        List<LedgerEntryCommand> commands = new ArrayList<>();
        // Debit: Release Principal from Escrow
        commands.add(new LedgerEntryCommand(escrowAccount.getId(), EntryType.DEBIT, payout.getAmount()));
        // Debit: Reverse Platform Fee
        commands.add(new LedgerEntryCommand(feeAccount.getId(), EntryType.DEBIT, payout.getFee()));
        // Credit: Refund Full Balance to User Account
        commands.add(new LedgerEntryCommand(userAccount.getId(), EntryType.CREDIT, refundAmount));

        LedgerPostingRequest reversalRequest = new LedgerPostingRequest(
                reversalRef,
                "Outbound Payout Reversal / Refund: " + reference + " (" + failureReason + ")",
                reversalRef,
                commands
        );
        ledgerService.postTransaction(reversalRequest);

        payout.markReversed(failureReason != null ? failureReason : "Bank settlement failed");
        payout = payoutRepository.save(payout);

        log.info("Payout {} reversed and fully refunded to user {}", reference, userAccount.getAccountNumber());
        return mapToResponse(payout);
    }

    @Override
    @Transactional(readOnly = true)
    public PayoutResponse getPayoutByReference(String reference) {
        Payout payout = payoutRepository.findByReference(reference)
                .orElseThrow(() -> new PayoutNotFoundException(reference));
        return mapToResponse(payout);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayoutResponse> getUserPayouts(UUID userId) {
        return payoutRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private PayoutResponse mapToResponse(Payout p) {
        return new PayoutResponse(
                p.getId(),
                p.getReference(),
                p.getAccount().getId(),
                p.getAccount().getAccountNumber(),
                p.getAmount(),
                p.getFee(),
                p.getCurrency(),
                p.getStatus(),
                p.getDestinationBankCode(),
                p.getDestinationAccountNumber(),
                p.getDestinationAccountName(),
                p.getFailureReason(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    private String generatePayoutReference() {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int randomDigits = 100000 + secureRandom.nextInt(900000);
        return String.format("PO-%s-%06d", datePrefix, randomDigits);
    }
}
