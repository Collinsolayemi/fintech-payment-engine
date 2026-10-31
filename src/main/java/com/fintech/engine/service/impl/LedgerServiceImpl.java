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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LedgerServiceImpl implements LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerServiceImpl.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerServiceImpl(AccountRepository accountRepository,
                             TransactionRepository transactionRepository,
                             LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse postTransaction(LedgerPostingRequest request) {
        log.info("Processing financial transaction reference: {}", request.reference());

        // 1. Validate that the posting is balanced: sum(Debits) == sum(Credits)
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        for (LedgerEntryCommand entryCmd : request.entries()) {
            if (entryCmd.amount() == null || entryCmd.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidAmountException(entryCmd.amount());
            }

            if (entryCmd.entryType() == EntryType.DEBIT) {
                totalDebits = totalDebits.add(entryCmd.amount());
            } else if (entryCmd.entryType() == EntryType.CREDIT) {
                totalCredits = totalCredits.add(entryCmd.amount());
            }
        }

        if (totalDebits.compareTo(totalCredits) != 0) {
            log.error("Double-entry violation! Debits ({}) != Credits ({}) for ref: {}",
                    totalDebits, totalCredits, request.reference());
            throw new UnbalancedLedgerException(totalDebits, totalCredits);
        }

        // 2. Deadlock Prevention: Sort distinct account IDs in natural order before locking
        List<UUID> sortedAccountIds = request.entries().stream()
                .map(LedgerEntryCommand::accountId)
                .distinct()
                .sorted(UUID::compareTo)
                .collect(Collectors.toList());

        Map<UUID, Account> lockedAccounts = new HashMap<>();
        for (UUID accountId : sortedAccountIds) {
            Account account = accountRepository.findByIdForUpdate(accountId)
                    .orElseThrow(() -> new AccountNotFoundException(accountId));

            if (account.getStatus() != AccountStatus.ACTIVE) {
                throw new IllegalStateException("Account " + account.getAccountNumber() + " is not ACTIVE");
            }

            lockedAccounts.put(accountId, account);
        }

        // 3. Persist Transaction Header
        Transaction transaction = new Transaction(request.reference(), request.idempotencyKey(), request.description());
        transaction = transactionRepository.save(transaction);

        // 4. Persist Immutable Ledger Entries
        List<LedgerEntryResponse> entryResponses = new ArrayList<>();
        for (LedgerEntryCommand entryCmd : request.entries()) {
            Account account = lockedAccounts.get(entryCmd.accountId());
            LedgerEntry entry = new LedgerEntry(transaction, account, entryCmd.entryType(), entryCmd.amount());
            entry = ledgerEntryRepository.save(entry);

            entryResponses.add(new LedgerEntryResponse(
                    entry.getId(),
                    account.getId(),
                    account.getAccountNumber(),
                    entry.getEntryType(),
                    entry.getAmount(),
                    entry.getCreatedAt()
            ));
        }

        // 5. Mark Transaction as COMPLETED
        transaction.markCompleted();
        transaction = transactionRepository.save(transaction);

        log.info("Successfully posted balanced transaction {}: {} entries processed",
                transaction.getReference(), entryResponses.size());

        return new TransactionResponse(
                transaction.getId(),
                transaction.getReference(),
                transaction.getIdempotencyKey(),
                transaction.getStatus(),
                transaction.getDescription(),
                transaction.getCreatedAt(),
                transaction.getCompletedAt(),
                entryResponses
        );
    }

    @Override
    @Transactional(readOnly = true)
    public AccountBalanceResponse getAccountBalance(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        BigDecimal debits = ledgerEntryRepository.sumAmountByAccountIdAndEntryType(accountId, EntryType.DEBIT);
        BigDecimal credits = ledgerEntryRepository.sumAmountByAccountIdAndEntryType(accountId, EntryType.CREDIT);

        // Calculate balance according to accounting rules:
        // Asset/Expense: Balance = Debits - Credits
        // Liability/Equity/Revenue: Balance = Credits - Debits
        BigDecimal derivedBalance = account.getType().isDebitNormal()
                ? debits.subtract(credits)
                : credits.subtract(debits);

        return new AccountBalanceResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getAccountName(),
                account.getType(),
                account.getCurrency(),
                account.getStatus(),
                derivedBalance,
                debits,
                credits
        );
    }

    @Override
    @Transactional(readOnly = true)
    public boolean verifyGlobalIntegrity() {
        BigDecimal totalDebits = ledgerEntryRepository.sumTotalByEntryType(EntryType.DEBIT);
        BigDecimal totalCredits = ledgerEntryRepository.sumTotalByEntryType(EntryType.CREDIT);

        boolean isBalanced = totalDebits.compareTo(totalCredits) == 0;
        if (!isBalanced) {
            log.error("CRITICAL AUDIT FAILURE: Global ledger is unbalanced! Total Debits: {}, Total Credits: {}",
                    totalDebits, totalCredits);
        }
        return isBalanced;
    }
}

