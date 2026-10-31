package com.fintech.engine.service.impl;

import com.fintech.engine.dto.*;
import com.fintech.engine.exception.*;
import com.fintech.engine.model.entity.Account;
import com.fintech.engine.model.entity.LedgerEntry;
import com.fintech.engine.model.entity.Transaction;
import com.fintech.engine.model.enums.AccountStatus;
import com.fintech.engine.model.enums.EntryType;
import com.fintech.engine.repository.AccountRepository;
import com.fintech.engine.repository.LedgerEntryRepository;
import com.fintech.engine.repository.TransactionRepository;
import com.fintech.engine.service.LedgerService;
import com.fintech.engine.service.TransferService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class TransferServiceImpl implements TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferServiceImpl.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerService ledgerService;
    private final SecureRandom secureRandom = new SecureRandom();

    public TransferServiceImpl(AccountRepository accountRepository,
                               TransactionRepository transactionRepository,
                               LedgerEntryRepository ledgerEntryRepository,
                               LedgerService ledgerService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.ledgerService = ledgerService;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransferResponse transfer(TransferRequest request, String idempotencyKey, String authenticatedUserEmail) {
        UUID senderId = request.senderAccountId();
        UUID recipientId = request.recipientAccountId();

        // 1. Validation: sender != recipient
        if (senderId.equals(recipientId)) {
            throw new SelfTransferException(senderId);
        }

        BigDecimal amount = request.amount();
        BigDecimal fee = request.resolvedFee();

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException(amount);
        }
        if (fee.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidAmountException(fee);
        }

        // 2. Deadlock Prevention: Deterministic ordered locking
        UUID firstId = senderId.compareTo(recipientId) < 0 ? senderId : recipientId;
        UUID secondId = senderId.compareTo(recipientId) < 0 ? recipientId : senderId;

        Account firstAccount = accountRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> new AccountNotFoundException(firstId));
        Account secondAccount = accountRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> new AccountNotFoundException(secondId));

        Account senderAccount = senderId.equals(firstId) ? firstAccount : secondAccount;
        Account recipientAccount = recipientId.equals(firstId) ? firstAccount : secondAccount;

        // 3. Ownership Verification Guard
        if (authenticatedUserEmail != null && senderAccount.getUser() != null) {
            if (!senderAccount.getUser().getEmail().equalsIgnoreCase(authenticatedUserEmail)) {
                log.warn("Security violation: User {} attempted to debit account {} belonging to {}",
                        authenticatedUserEmail, senderId, senderAccount.getUser().getEmail());
                throw new UnauthorizedAccountAccessException(senderId, authenticatedUserEmail);
            }
        }

        // 4. Status checks
        if (senderAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Sender account " + senderAccount.getAccountNumber() + " is " + senderAccount.getStatus());
        }
        if (recipientAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Recipient account " + recipientAccount.getAccountNumber() + " is " + recipientAccount.getStatus());
        }

        // 4. Currency matching check
        if (!senderAccount.getCurrency().equalsIgnoreCase(recipientAccount.getCurrency())) {
            throw new CurrencyMismatchException(senderAccount.getCurrency(), recipientAccount.getCurrency());
        }

        // 5. Balance check against derived ledger balance
        BigDecimal totalDebitRequired = amount.add(fee);
        BigDecimal senderAvailable = ledgerService.getAccountBalance(senderId).balance();

        if (senderAvailable.compareTo(totalDebitRequired) < 0) {
            log.warn("Transfer failed: Insufficient funds for account {}. Available: {}, Required: {}",
                    senderId, senderAvailable, totalDebitRequired);
            throw new InsufficientFundsException(senderId, senderAvailable, totalDebitRequired);
        }

        // 6. Assemble balanced journal entries
        String reference = generateTransferReference();
        List<LedgerEntryCommand> entries = new ArrayList<>();

        // Debit sender wallet (total amount + fee)
        entries.add(new LedgerEntryCommand(senderId, EntryType.DEBIT, totalDebitRequired));

        // Credit recipient wallet (amount)
        entries.add(new LedgerEntryCommand(recipientId, EntryType.CREDIT, amount));

        // Credit platform revenue if fee > 0
        if (fee.compareTo(BigDecimal.ZERO) > 0) {
            String revenueAccountNumber = "SYS-4001-" + senderAccount.getCurrency();
            Account revenueAccount = accountRepository.findByAccountNumber(revenueAccountNumber)
                    .orElseThrow(() -> new AccountNotFoundException(revenueAccountNumber));
            entries.add(new LedgerEntryCommand(revenueAccount.getId(), EntryType.CREDIT, fee));
        }

        LedgerPostingRequest postingRequest = new LedgerPostingRequest(
                reference,
                idempotencyKey,
                request.description() != null ? request.description() : "P2P transfer",
                entries
        );

        TransactionResponse txResponse = ledgerService.postTransaction(postingRequest);

        log.info("P2P transfer successful: ref={}, sender={}, recipient={}, amount={}, fee={}",
                reference, senderId, recipientId, amount, fee);

        return new TransferResponse(
                reference,
                idempotencyKey,
                txResponse.status(),
                amount,
                fee,
                senderAccount.getCurrency(),
                senderId,
                recipientId,
                request.description(),
                txResponse.completedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public TransferResponse getTransferByReference(String reference) {
        Transaction tx = transactionRepository.findByReference(reference)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found for reference: " + reference));

        List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(tx.getId());

        UUID senderAccountId = null;
        UUID recipientAccountId = null;
        BigDecimal transferAmount = BigDecimal.ZERO;
        BigDecimal fee = BigDecimal.ZERO;
        String currency = "USD";

        for (LedgerEntry entry : entries) {
            if (entry.getEntryType() == EntryType.DEBIT) {
                senderAccountId = entry.getAccount().getId();
                currency = entry.getAccount().getCurrency();
            } else if (entry.getEntryType() == EntryType.CREDIT) {
                String accNum = entry.getAccount().getAccountNumber();
                if (accNum != null && accNum.startsWith("SYS-4001")) {
                    fee = entry.getAmount();
                } else {
                    recipientAccountId = entry.getAccount().getId();
                    transferAmount = entry.getAmount();
                    currency = entry.getAccount().getCurrency();
                }
            }
        }

        return new TransferResponse(
                tx.getReference(),
                tx.getIdempotencyKey(),
                tx.getStatus(),
                transferAmount,
                fee,
                currency,
                senderAccountId,
                recipientAccountId,
                tx.getDescription(),
                tx.getCompletedAt()
        );
    }

    private String generateTransferReference() {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int randomDigits = 100000 + secureRandom.nextInt(900000);
        return String.format("TX-%s-%06d", datePrefix, randomDigits);
    }
}

