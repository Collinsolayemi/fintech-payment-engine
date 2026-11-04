package com.fintech.engine.service;

import com.fintech.engine.dto.DepositRequest;
import com.fintech.engine.dto.LedgerPostingRequest;
import com.fintech.engine.dto.TransactionResponse;
import com.fintech.engine.dto.TransactionSummaryResponse;
import com.fintech.engine.exception.TransactionNotFoundException;
import com.fintech.engine.exception.UserNotFoundException;
import com.fintech.engine.model.entity.Account;
import com.fintech.engine.model.entity.LedgerEntry;
import com.fintech.engine.model.entity.Transaction;
import com.fintech.engine.model.entity.User;
import com.fintech.engine.model.enums.AccountType;
import com.fintech.engine.model.enums.EntryType;
import com.fintech.engine.model.enums.TransactionStatus;
import com.fintech.engine.repository.AccountRepository;
import com.fintech.engine.repository.LedgerEntryRepository;
import com.fintech.engine.repository.TransactionRepository;
import com.fintech.engine.repository.UserRepository;
import com.fintech.engine.service.impl.TransactionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LedgerService ledgerService;

    private TransactionServiceImpl transactionService;

    private User testUser;
    private Account userAccount;
    private Account clearingAccount;
    private Transaction testTransaction;
    private LedgerEntry debitEntry;
    private LedgerEntry creditEntry;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionServiceImpl(
                transactionRepository,
                ledgerEntryRepository,
                accountRepository,
                userRepository,
                ledgerService
        );

        testUser = new User("user@example.com", "hashedPassword", "John", "Doe");
        testUser.setId(UUID.randomUUID());

        userAccount = new Account(testUser, "USD-11223344", "John Wallet", AccountType.LIABILITY, "USD");
        userAccount.setId(UUID.randomUUID());

        clearingAccount = new Account(null, "SYS-1002-USD", "GATEWAY_CLEARING", AccountType.ASSET, "USD");
        clearingAccount.setId(UUID.randomUUID());

        testTransaction = new Transaction("TX-TEST-001", "IDEM-001", "Peer to peer transfer");
        testTransaction.setId(UUID.randomUUID());
        testTransaction.setStatus(TransactionStatus.COMPLETED);

        debitEntry = new LedgerEntry(testTransaction, userAccount, EntryType.DEBIT, new BigDecimal("50.00"));
        creditEntry = new LedgerEntry(testTransaction, userAccount, EntryType.CREDIT, new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("getUserTransactions - Should return mapped summaries with signed amounts for LIABILITY account")
    void shouldReturnUserTransactionsWithSignedAmounts() {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(ledgerEntryRepository.findByUserIdOrderByCreatedAtDesc(testUser.getId()))
                .thenReturn(List.of(debitEntry, creditEntry));

        List<TransactionSummaryResponse> result = transactionService.getUserTransactions(testUser.getId());

        assertThat(result).hasSize(2);
        // Debit on liability = money out -> negative amount
        assertThat(result.get(0).amount()).isEqualByComparingTo(new BigDecimal("-50.00"));
        assertThat(result.get(0).reference()).isEqualTo("TX-TEST-001");
        // Credit on liability = money in -> positive amount
        assertThat(result.get(1).amount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("getUserTransactions - Should throw UserNotFoundException if user does not exist")
    void shouldThrowWhenUserNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getUserTransactions(unknownId))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("getAccountTransactions - Should return paginated results")
    void shouldReturnPaginatedAccountTransactions() {
        when(accountRepository.findById(userAccount.getId())).thenReturn(Optional.of(userAccount));
        PageRequest pageRequest = PageRequest.of(0, 10);
        when(ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(userAccount.getId(), pageRequest))
                .thenReturn(new PageImpl<>(List.of(creditEntry), pageRequest, 1));

        Page<TransactionSummaryResponse> page = transactionService.getAccountTransactions(userAccount.getId(), pageRequest);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).amount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("getTransactionByReference - Should return transaction with double-entry journal postings")
    void shouldReturnTransactionWithJournalBreakdown() {
        when(transactionRepository.findByReference("TX-TEST-001")).thenReturn(Optional.of(testTransaction));
        when(ledgerEntryRepository.findByTransactionId(testTransaction.getId()))
                .thenReturn(List.of(debitEntry, creditEntry));

        TransactionResponse response = transactionService.getTransactionByReference("TX-TEST-001");

        assertThat(response.reference()).isEqualTo("TX-TEST-001");
        assertThat(response.entries()).hasSize(2);
    }

    @Test
    @DisplayName("getTransactionByReference - Should throw TransactionNotFoundException if reference invalid")
    void shouldThrowWhenTransactionReferenceNotFound() {
        when(transactionRepository.findByReference("INVALID")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getTransactionByReference("INVALID"))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    @DisplayName("exportAccountStatementCsv - Should format CSV stream with headers and signed amounts")
    void shouldExportAccountStatementCsv() {
        when(accountRepository.findById(userAccount.getId())).thenReturn(Optional.of(userAccount));
        when(ledgerEntryRepository.findByAccountIdAndDateRange(eq(userAccount.getId()), any(), any()))
                .thenReturn(List.of(debitEntry, creditEntry));

        byte[] csvBytes = transactionService.exportAccountStatementCsv(userAccount.getId(), null, null);
        String csvContent = new String(csvBytes, StandardCharsets.UTF_8);

        assertThat(csvContent).contains("Timestamp,Transaction Reference,Type,Amount,Currency,Account Number,Description,Status");
        assertThat(csvContent).contains("TX-TEST-001,DEBIT,-50.00,USD,USD-11223344");
        assertThat(csvContent).contains("TX-TEST-001,CREDIT,100.00,USD,USD-11223344");
    }

    @Test
    @DisplayName("deposit - Should execute balanced double-entry (Debit clearing, Credit user wallet)")
    void shouldExecuteBalancedDeposit() {
        DepositRequest request = new DepositRequest(
                userAccount.getId(),
                new BigDecimal("250.00"),
                "USD",
                "Instant wallet top-up"
        );

        when(accountRepository.findById(userAccount.getId())).thenReturn(Optional.of(userAccount));
        when(accountRepository.findByAccountNumber("SYS-1002-USD")).thenReturn(Optional.of(clearingAccount));

        TransactionResponse mockResponse = new TransactionResponse(
                UUID.randomUUID(),
                "DEP-20261104-123456",
                "IDEM-DEP-1",
                TransactionStatus.COMPLETED,
                "Instant wallet top-up",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                List.of()
        );
        when(ledgerService.postTransaction(any(LedgerPostingRequest.class))).thenReturn(mockResponse);

        TransactionResponse result = transactionService.deposit(request, "IDEM-DEP-1", "user@example.com");

        assertThat(result.reference()).startsWith("DEP-");
        verify(ledgerService).postTransaction(argThat(postReq -> {
            assertThat(postReq.entries()).hasSize(2);
            assertThat(postReq.entries().get(0).entryType()).isEqualTo(EntryType.DEBIT);
            assertThat(postReq.entries().get(0).amount()).isEqualByComparingTo(new BigDecimal("250.00"));
            assertThat(postReq.entries().get(1).entryType()).isEqualTo(EntryType.CREDIT);
            assertThat(postReq.entries().get(1).amount()).isEqualByComparingTo(new BigDecimal("250.00"));
            return true;
        }));
    }
}

