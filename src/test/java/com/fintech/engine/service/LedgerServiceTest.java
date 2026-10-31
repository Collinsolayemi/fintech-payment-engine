package com.fintech.engine.service;

import com.fintech.engine.dto.*;
import com.fintech.engine.exception.InvalidAmountException;
import com.fintech.engine.exception.UnbalancedLedgerException;
import com.fintech.engine.model.entity.Account;
import com.fintech.engine.model.entity.Transaction;
import com.fintech.engine.model.enums.AccountStatus;
import com.fintech.engine.model.enums.AccountType;
import com.fintech.engine.model.enums.EntryType;
import com.fintech.engine.model.enums.TransactionStatus;
import com.fintech.engine.repository.AccountRepository;
import com.fintech.engine.repository.LedgerEntryRepository;
import com.fintech.engine.repository.TransactionRepository;
import com.fintech.engine.service.impl.LedgerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    private LedgerServiceImpl ledgerService;

    private UUID userWalletId;
    private UUID gatewayAssetId;
    private Account userWallet;
    private Account gatewayAsset;

    @BeforeEach
    void setUp() {
        ledgerService = new LedgerServiceImpl(accountRepository, transactionRepository, ledgerEntryRepository);

        userWalletId = UUID.randomUUID();
        gatewayAssetId = UUID.randomUUID();

        userWallet = new Account(null, "USD-11223344", "John Doe Wallet", AccountType.LIABILITY, "USD");
        userWallet.setId(userWalletId);
        userWallet.setStatus(AccountStatus.ACTIVE);

        gatewayAsset = new Account(null, "SYS-1002-USD", "Stripe Gateway", AccountType.ASSET, "USD");
        gatewayAsset.setId(gatewayAssetId);
        gatewayAsset.setStatus(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should successfully post a balanced double-entry transaction")
    void shouldPostBalancedTransactionSuccessfully() {
        // Arrange
        BigDecimal depositAmount = new BigDecimal("100.00");
        LedgerPostingRequest request = new LedgerPostingRequest(
                "DEP-20261031-001",
                "idem-key-1001",
                "Stripe deposit",
                List.of(
                        new LedgerEntryCommand(gatewayAssetId, EntryType.DEBIT, depositAmount),
                        new LedgerEntryCommand(userWalletId, EntryType.CREDIT, depositAmount)
                )
        );

        when(accountRepository.findByIdForUpdate(userWalletId)).thenReturn(Optional.of(userWallet));
        when(accountRepository.findByIdForUpdate(gatewayAssetId)).thenReturn(Optional.of(gatewayAsset));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        TransactionResponse response = ledgerService.postTransaction(request);

        // Assert
        assertNotNull(response);
        assertEquals("DEP-20261031-001", response.reference());
        assertEquals(TransactionStatus.COMPLETED, response.status());
        assertEquals(2, response.entries().size());
        verify(ledgerEntryRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("Should throw UnbalancedLedgerException when debits do not match credits")
    void shouldThrowUnbalancedLedgerExceptionWhenDebitsDoNotMatchCredits() {
        // Arrange: $100 Debit vs $90 Credit
        LedgerPostingRequest unbalancedRequest = new LedgerPostingRequest(
                "UNBALANCED-TX",
                "idem-unbal",
                "Broken transaction",
                List.of(
                        new LedgerEntryCommand(gatewayAssetId, EntryType.DEBIT, new BigDecimal("100.00")),
                        new LedgerEntryCommand(userWalletId, EntryType.CREDIT, new BigDecimal("90.00"))
                )
        );

        // Act & Assert
        UnbalancedLedgerException exception = assertThrows(UnbalancedLedgerException.class, () ->
                ledgerService.postTransaction(unbalancedRequest)
        );

        assertEquals("UNBALANCED_LEDGER", exception.getErrorCode());
        verifyNoInteractions(accountRepository);
        verifyNoInteractions(ledgerEntryRepository);
    }

    @Test
    @DisplayName("Should throw InvalidAmountException for zero or negative amounts")
    void shouldThrowInvalidAmountExceptionForZeroOrNegativeAmounts() {
        LedgerPostingRequest invalidAmountRequest = new LedgerPostingRequest(
                "ZERO-AMOUNT-TX",
                "idem-zero",
                "Zero amount test",
                List.of(
                        new LedgerEntryCommand(gatewayAssetId, EntryType.DEBIT, BigDecimal.ZERO),
                        new LedgerEntryCommand(userWalletId, EntryType.CREDIT, BigDecimal.ZERO)
                )
        );

        assertThrows(InvalidAmountException.class, () ->
                ledgerService.postTransaction(invalidAmountRequest)
        );
    }

    @Test
    @DisplayName("Should correctly derive balance for Liability (User Wallet: Credits - Debits)")
    void shouldDeriveBalanceForLiabilityAccount() {
        // In user wallets (Liability), Credits increase balance and Debits decrease balance
        when(accountRepository.findById(userWalletId)).thenReturn(Optional.of(userWallet));
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryType(userWalletId, EntryType.CREDIT))
                .thenReturn(new BigDecimal("250.00"));
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryType(userWalletId, EntryType.DEBIT))
                .thenReturn(new BigDecimal("50.00"));

        AccountBalanceResponse balanceResponse = ledgerService.getAccountBalance(userWalletId);

        assertNotNull(balanceResponse);
        // Balance = 250 - 50 = 200.00
        assertEquals(new BigDecimal("200.00"), balanceResponse.balance());
    }

    @Test
    @DisplayName("Should correctly derive balance for Asset (Gateway: Debits - Credits)")
    void shouldDeriveBalanceForAssetAccount() {
        // In asset accounts, Debits increase balance and Credits decrease balance
        when(accountRepository.findById(gatewayAssetId)).thenReturn(Optional.of(gatewayAsset));
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryType(gatewayAssetId, EntryType.DEBIT))
                .thenReturn(new BigDecimal("500.00"));
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryType(gatewayAssetId, EntryType.CREDIT))
                .thenReturn(new BigDecimal("100.00"));

        AccountBalanceResponse balanceResponse = ledgerService.getAccountBalance(gatewayAssetId);

        assertNotNull(balanceResponse);
        // Balance = 500 - 100 = 400.00
        assertEquals(new BigDecimal("400.00"), balanceResponse.balance());
    }
}
