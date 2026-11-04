package com.fintech.engine.service.impl;

import com.fintech.engine.dto.*;
import com.fintech.engine.exception.*;
import com.fintech.engine.exception.InvalidAmountException;
import com.fintech.engine.model.entity.Account;
import com.fintech.engine.model.entity.LedgerEntry;
import com.fintech.engine.model.entity.Transaction;
import com.fintech.engine.model.enums.AccountStatus;
import com.fintech.engine.model.enums.AccountType;
import com.fintech.engine.model.enums.EntryType;
import com.fintech.engine.repository.AccountRepository;
import com.fintech.engine.repository.LedgerEntryRepository;
import com.fintech.engine.repository.TransactionRepository;
import com.fintech.engine.repository.UserRepository;
import com.fintech.engine.service.LedgerService;
import com.fintech.engine.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TransactionServiceImpl implements TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionServiceImpl.class);

    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final LedgerService ledgerService;
    private final SecureRandom secureRandom = new SecureRandom();

    public TransactionServiceImpl(
            TransactionRepository transactionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            AccountRepository accountRepository,
            UserRepository userRepository,
            LedgerService ledgerService
    ) {
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.ledgerService = ledgerService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionSummaryResponse> getUserTransactions(UUID userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId));

        List<LedgerEntry> entries = ledgerEntryRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return entries.stream()
                .map(this::mapToSummary)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionSummaryResponse> getUserTransactions(UUID userId, Pageable pageable) {
        userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId));

        return ledgerEntryRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionSummaryResponse> getAccountTransactions(UUID accountId, Pageable pageable) {
        accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        return ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable)
                .map(this::mapToSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse getTransactionByReference(String reference) {
        Transaction transaction = transactionRepository.findByReference(reference)
                .orElseThrow(() -> new TransactionNotFoundException(reference));

        List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(transaction.getId());

        List<LedgerEntryResponse> entryResponses = entries.stream()
                .map(e -> new LedgerEntryResponse(
                        e.getId(),
                        e.getAccount().getId(),
                        e.getAccount().getAccountNumber(),
                        e.getEntryType(),
                        e.getAmount(),
                        e.getCreatedAt()
                ))
                .collect(Collectors.toList());

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
    public byte[] exportAccountStatementCsv(UUID accountId, OffsetDateTime from, OffsetDateTime to) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        OffsetDateTime start = from != null ? from : OffsetDateTime.now().minusDays(30);
        OffsetDateTime end = to != null ? to : OffsetDateTime.now();

        List<LedgerEntry> entries = ledgerEntryRepository.findByAccountIdAndDateRange(accountId, start, end);

        StringBuilder sb = new StringBuilder();
        sb.append("Timestamp,Transaction Reference,Type,Amount,Currency,Account Number,Description,Status\n");

        for (LedgerEntry entry : entries) {
            Transaction tx = entry.getTransaction();
            BigDecimal signedAmount = entry.getEntryType() == EntryType.DEBIT ? entry.getAmount().negate() : entry.getAmount();

            sb.append(entry.getCreatedAt()).append(",")
                    .append(escapeCsv(tx.getReference())).append(",")
                    .append(entry.getEntryType().name()).append(",")
                    .append(signedAmount).append(",")
                    .append(account.getCurrency()).append(",")
                    .append(escapeCsv(account.getAccountNumber())).append(",")
                    .append(escapeCsv(tx.getDescription() != null ? tx.getDescription() : "")).append(",")
                    .append(tx.getStatus().name()).append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    @Transactional
    public TransactionResponse deposit(DepositRequest request, String idempotencyKey, String authenticatedEmail) {
        Account destinationAccount = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new AccountNotFoundException(request.accountId()));

        if (destinationAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Destination account is not ACTIVE");
        }

        if (authenticatedEmail != null && destinationAccount.getUser() != null) {
            boolean isOwner = destinationAccount.getUser().getEmail().equalsIgnoreCase(authenticatedEmail);
            boolean isAdmin = destinationAccount.getUser().getRole() != null &&
                    destinationAccount.getUser().getRole().equals("ROLE_ADMIN");
            if (!isOwner && !isAdmin) {
                throw new UnauthorizedAccountAccessException(destinationAccount.getId(), authenticatedEmail);
            }
        }

        BigDecimal amount = request.amount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException(amount);
        }

        String currency = destinationAccount.getCurrency();
        String gatewayAccountNumber = "SYS-1002-" + currency;
        Account clearingAccount = accountRepository.findByAccountNumber(gatewayAccountNumber)
                .orElseGet(() -> accountRepository.findByAccountNumber("SYS-1001-" + currency)
                        .orElseThrow(() -> new AccountNotFoundException("Platform clearing account not found for currency: " + currency)));

        String reference = generateDepositReference();
        String description = request.description() != null && !request.description().isBlank()
                ? request.description()
                : "Wallet top-up deposit";

        log.info("Processing wallet deposit: reference={}, destinationAccount={}, amount={} {}",
                reference, destinationAccount.getAccountNumber(), amount, currency);

        List<LedgerEntryCommand> entries = List.of(
                new LedgerEntryCommand(clearingAccount.getId(), EntryType.DEBIT, amount),
                new LedgerEntryCommand(destinationAccount.getId(), EntryType.CREDIT, amount)
        );

        LedgerPostingRequest postingRequest = new LedgerPostingRequest(
                reference,
                idempotencyKey,
                description,
                entries
        );

        return ledgerService.postTransaction(postingRequest);
    }

    private TransactionSummaryResponse mapToSummary(LedgerEntry entry) {
        Transaction tx = entry.getTransaction();
        Account acc = entry.getAccount();

        // For LIABILITY (user wallet):
        // DEBIT = money going out (decrease liability) -> -amount
        // CREDIT = money coming in (increase liability) -> +amount
        BigDecimal signedAmount = entry.getAmount();
        if (acc.getType() == AccountType.LIABILITY) {
            signedAmount = entry.getEntryType() == EntryType.DEBIT ? entry.getAmount().negate() : entry.getAmount();
        }

        return new TransactionSummaryResponse(
                tx.getId(),
                tx.getReference(),
                tx.getDescription(),
                tx.getStatus().name(),
                signedAmount,
                acc.getCurrency(),
                entry.getEntryType(),
                acc.getId(),
                acc.getAccountNumber(),
                entry.getCreatedAt()
        );
    }

    private String generateDepositReference() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int rand = secureRandom.nextInt(900000) + 100000;
        return "DEP-" + date + "-" + rand;
    }

    private String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
