package com.fintech.engine.service;

import com.fintech.engine.dto.InitiatePayoutRequest;
import com.fintech.engine.dto.LedgerPostingRequest;
import com.fintech.engine.dto.PayoutResponse;
import com.fintech.engine.dto.TransactionResponse;
import com.fintech.engine.exception.InsufficientFundsException;
import com.fintech.engine.model.entity.Account;
import com.fintech.engine.model.entity.Payout;
import com.fintech.engine.model.entity.User;
import com.fintech.engine.model.enums.AccountType;
import com.fintech.engine.model.enums.PayoutStatus;
import com.fintech.engine.repository.AccountRepository;
import com.fintech.engine.repository.PayoutRepository;
import com.fintech.engine.repository.UserRepository;
import com.fintech.engine.service.impl.PayoutServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayoutServiceTest {

    @Mock
    private PayoutRepository payoutRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private KycLimitService kycLimitService;

    private PayoutServiceImpl payoutService;

    private UUID userId;
    private UUID userAccountId;
    private UUID escrowAccountId;
    private UUID feeAccountId;
    private UUID bankClearingAccountId;

    private User user;
    private Account userAccount;
    private Account escrowAccount;
    private Account feeAccount;
    private Account bankClearingAccount;

    @BeforeEach
    void setUp() {
        payoutService = new PayoutServiceImpl(
                payoutRepository,
                accountRepository,
                userRepository,
                ledgerService,
                kycLimitService
        );
        ReflectionTestUtils.setField(payoutService, "flatPayoutFee", new BigDecimal("2.00"));

        userId = UUID.randomUUID();
        userAccountId = UUID.randomUUID();
        escrowAccountId = UUID.randomUUID();
        feeAccountId = UUID.randomUUID();
        bankClearingAccountId = UUID.randomUUID();

        user = new User("user@example.com", "hash", "John", "Doe");
        user.setId(userId);

        userAccount = new Account(user, "USD-USER-001", "User Wallet", AccountType.LIABILITY, "USD");
        userAccount.setId(userAccountId);

        escrowAccount = new Account(null, "SYS-2002-USD", "Pending Escrow", AccountType.LIABILITY, "USD");
        escrowAccount.setId(escrowAccountId);

        feeAccount = new Account(null, "SYS-4001-USD", "Fee Revenue", AccountType.REVENUE, "USD");
        feeAccount.setId(feeAccountId);

        bankClearingAccount = new Account(null, "SYS-1001-USD", "Bank Clearing", AccountType.ASSET, "USD");
        bankClearingAccount.setId(bankClearingAccountId);
    }

    @Test
    @DisplayName("Should successfully initiate payout with Phase 1 escrow lock")
    void shouldInitiatePayoutSuccessfully() {
        InitiatePayoutRequest request = new InitiatePayoutRequest(
                userAccountId,
                new BigDecimal("100.00"),
                "USD",
                "CHASE001",
                "9876543210",
                "John Doe",
                "Withdrawal to Chase"
        );

        when(accountRepository.findById(userAccountId)).thenReturn(Optional.of(userAccount));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(accountRepository.findByAccountNumber("SYS-2002-USD")).thenReturn(Optional.of(escrowAccount));
        when(accountRepository.findByAccountNumber("SYS-4001-USD")).thenReturn(Optional.of(feeAccount));
        com.fintech.engine.dto.AccountBalanceResponse balanceResponse = new com.fintech.engine.dto.AccountBalanceResponse(
                userAccountId, "USD-USER-001", "User Wallet", AccountType.LIABILITY, "USD",
                com.fintech.engine.model.enums.AccountStatus.ACTIVE, new BigDecimal("200.00"), BigDecimal.ZERO, new BigDecimal("200.00")
        );
        when(ledgerService.getAccountBalance(userAccountId)).thenReturn(balanceResponse);

        TransactionResponse dummyTx = new TransactionResponse(
                UUID.randomUUID(), "TX-REF", "IDEMP", com.fintech.engine.model.enums.TransactionStatus.COMPLETED,
                "desc", java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now(), java.util.List.of()
        );
        when(ledgerService.postTransaction(any(LedgerPostingRequest.class))).thenReturn(dummyTx);
        when(payoutRepository.save(any(Payout.class))).thenAnswer(i -> {
            Payout p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        PayoutResponse response = payoutService.initiatePayout(request, "idemp-payout-1", "user@example.com");

        assertNotNull(response);
        assertEquals(PayoutStatus.PROCESSING, response.status());
        assertEquals(new BigDecimal("100.00"), response.amount());
        assertEquals(new BigDecimal("2.00"), response.fee());
        assertEquals("CHASE001", response.destinationBankCode());

        verify(ledgerService).postTransaction(any(LedgerPostingRequest.class));
        verify(payoutRepository).save(any(Payout.class));
    }

    @Test
    @DisplayName("Should reject payout initiation when user balance is insufficient for amount + fee")
    void shouldRejectOnInsufficientBalance() {
        InitiatePayoutRequest request = new InitiatePayoutRequest(
                userAccountId,
                new BigDecimal("100.00"),
                "USD",
                "CHASE001",
                "9876543210",
                "John Doe",
                "Withdrawal to Chase"
        );

        when(accountRepository.findById(userAccountId)).thenReturn(Optional.of(userAccount));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(accountRepository.findByAccountNumber("SYS-2002-USD")).thenReturn(Optional.of(escrowAccount));
        when(accountRepository.findByAccountNumber("SYS-4001-USD")).thenReturn(Optional.of(feeAccount));
        com.fintech.engine.dto.AccountBalanceResponse lowBalance = new com.fintech.engine.dto.AccountBalanceResponse(
                userAccountId, "USD-USER-001", "User Wallet", AccountType.LIABILITY, "USD",
                com.fintech.engine.model.enums.AccountStatus.ACTIVE, new BigDecimal("50.00"), BigDecimal.ZERO, new BigDecimal("50.00")
        );
        when(ledgerService.getAccountBalance(userAccountId)).thenReturn(lowBalance);

        assertThrows(InsufficientFundsException.class, () ->
                payoutService.initiatePayout(request, "idemp-payout-2", "user@example.com"));

        verify(payoutRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should complete payout settlement (Phase 2A) moving funds from Escrow to Clearing Bank")
    void shouldCompletePayoutSettlement() {
        Payout payout = new Payout(
                "PO-20261031-112233",
                user,
                userAccount,
                new BigDecimal("100.00"),
                new BigDecimal("2.00"),
                "USD",
                "CHASE001",
                "9876543210",
                "John Doe",
                "idemp-1"
        );
        payout.setId(UUID.randomUUID());
        payout.markProcessing();

        when(payoutRepository.findByReference("PO-20261031-112233")).thenReturn(Optional.of(payout));
        when(accountRepository.findByAccountNumber("SYS-2002-USD")).thenReturn(Optional.of(escrowAccount));
        when(accountRepository.findByAccountNumber("SYS-1001-USD")).thenReturn(Optional.of(bankClearingAccount));
        TransactionResponse dummyTx = new TransactionResponse(
                UUID.randomUUID(), "TX-REF", "IDEMP", com.fintech.engine.model.enums.TransactionStatus.COMPLETED,
                "desc", java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now(), java.util.List.of()
        );
        when(ledgerService.postTransaction(any(LedgerPostingRequest.class))).thenReturn(dummyTx);
        when(payoutRepository.save(any(Payout.class))).thenAnswer(i -> i.getArgument(0));

        PayoutResponse response = payoutService.completePayout("PO-20261031-112233");

        assertNotNull(response);
        assertEquals(PayoutStatus.COMPLETED, response.status());
        verify(ledgerService).postTransaction(any(LedgerPostingRequest.class));
        verify(payoutRepository).save(payout);
    }

    @Test
    @DisplayName("Should reverse failed payout (Phase 2B) releasing Escrow and refunding user wallet")
    void shouldReverseFailedPayout() {
        Payout payout = new Payout(
                "PO-20261031-998877",
                user,
                userAccount,
                new BigDecimal("100.00"),
                new BigDecimal("2.00"),
                "USD",
                "CHASE001",
                "9876543210",
                "John Doe",
                "idemp-2"
        );
        payout.setId(UUID.randomUUID());
        payout.markProcessing();

        when(payoutRepository.findByReference("PO-20261031-998877")).thenReturn(Optional.of(payout));
        when(accountRepository.findByAccountNumber("SYS-2002-USD")).thenReturn(Optional.of(escrowAccount));
        when(accountRepository.findByAccountNumber("SYS-4001-USD")).thenReturn(Optional.of(feeAccount));
        TransactionResponse dummyTx = new TransactionResponse(
                UUID.randomUUID(), "TX-REF", "IDEMP", com.fintech.engine.model.enums.TransactionStatus.COMPLETED,
                "desc", java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now(), java.util.List.of()
        );
        when(ledgerService.postTransaction(any(LedgerPostingRequest.class))).thenReturn(dummyTx);
        when(payoutRepository.save(any(Payout.class))).thenAnswer(i -> i.getArgument(0));

        PayoutResponse response = payoutService.reversePayout("PO-20261031-998877", "Account number closed at receiving bank");

        assertNotNull(response);
        assertEquals(PayoutStatus.REVERSED, response.status());
        assertEquals("Account number closed at receiving bank", response.failureReason());

        verify(ledgerService).postTransaction(any(LedgerPostingRequest.class));
        verify(payoutRepository).save(payout);
    }
}
