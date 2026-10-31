package com.fintech.engine.service;

import com.fintech.engine.dto.AccountBalanceResponse;
import com.fintech.engine.dto.LedgerPostingRequest;
import com.fintech.engine.dto.TransactionResponse;
import com.fintech.engine.dto.TransferRequest;
import com.fintech.engine.dto.TransferResponse;
import com.fintech.engine.exception.CurrencyMismatchException;
import com.fintech.engine.exception.InsufficientFundsException;
import com.fintech.engine.exception.InvalidAmountException;
import com.fintech.engine.exception.SelfTransferException;
import com.fintech.engine.exception.UnauthorizedAccountAccessException;
import com.fintech.engine.model.entity.Account;
import com.fintech.engine.model.entity.User;
import com.fintech.engine.model.enums.AccountStatus;
import com.fintech.engine.model.enums.AccountType;
import com.fintech.engine.model.enums.TransactionStatus;
import com.fintech.engine.repository.AccountRepository;
import com.fintech.engine.repository.TransactionRepository;
import com.fintech.engine.service.impl.TransferServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private com.fintech.engine.repository.LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private LedgerService ledgerService;

    private TransferServiceImpl transferService;

    private UUID senderId;
    private UUID recipientId;
    private Account senderAccount;
    private Account recipientAccount;
    private Account feeRevenueAccount;
    private User senderUser;

    @BeforeEach
    void setUp() {
        transferService = new TransferServiceImpl(accountRepository, transactionRepository, ledgerEntryRepository, ledgerService);

        senderId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        recipientId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        senderUser = new User("sender@example.com", "hashedPass", "Sender", "User");
        senderAccount = new Account(senderUser, "USD-1001", "Sender Wallet", AccountType.LIABILITY, "USD");
        senderAccount.setId(senderId);
        senderAccount.setStatus(AccountStatus.ACTIVE);

        recipientAccount = new Account(null, "USD-2002", "Recipient Wallet", AccountType.LIABILITY, "USD");
        recipientAccount.setId(recipientId);
        recipientAccount.setStatus(AccountStatus.ACTIVE);

        feeRevenueAccount = new Account(null, "SYS-4001-USD", "REVENUE_TRANSFER_FEES", AccountType.REVENUE, "USD");
        feeRevenueAccount.setId(UUID.fromString("00000000-0000-0000-0000-000000004001"));
        feeRevenueAccount.setStatus(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should successfully transfer funds when sender has sufficient balance and owns the account")
    void shouldTransferFundsSuccessfully() {
        TransferRequest request = new TransferRequest(
                senderId, recipientId, new BigDecimal("50.00"), BigDecimal.ZERO, "USD", "Dinner split"
        );

        when(accountRepository.findByIdForUpdate(senderId)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(recipientId)).thenReturn(Optional.of(recipientAccount));
        when(ledgerService.getAccountBalance(senderId)).thenReturn(new AccountBalanceResponse(
                senderId, "USD-1001", "Sender Wallet", AccountType.LIABILITY, "USD",
                AccountStatus.ACTIVE, new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("100.00")
        ));

        when(ledgerService.postTransaction(any(LedgerPostingRequest.class))).thenReturn(new TransactionResponse(
                UUID.randomUUID(), "TX-20261031-123456", "idem-001", TransactionStatus.COMPLETED,
                "Dinner split", OffsetDateTime.now(), OffsetDateTime.now(), Collections.emptyList()
        ));

        TransferResponse response = transferService.transfer(request, "idem-001", "sender@example.com");

        assertNotNull(response);
        assertEquals(TransactionStatus.COMPLETED, response.status());
        assertEquals(new BigDecimal("50.00"), response.amount());
        assertEquals(BigDecimal.ZERO, response.fee());
        assertEquals(senderId, response.senderAccountId());
        assertEquals(recipientId, response.recipientAccountId());
    }

    @Test
    @DisplayName("Should reject transfer when authenticated user does not own the sender account")
    void shouldRejectTransferWhenUserDoesNotOwnSenderAccount() {
        TransferRequest request = new TransferRequest(
                senderId, recipientId, new BigDecimal("50.00"), BigDecimal.ZERO, "USD", "Malicious attempt"
        );

        when(accountRepository.findByIdForUpdate(senderId)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(recipientId)).thenReturn(Optional.of(recipientAccount));

        UnauthorizedAccountAccessException ex = assertThrows(UnauthorizedAccountAccessException.class, () ->
                transferService.transfer(request, "idem-unauth", "attacker@example.com")
        );

        assertEquals("UNAUTHORIZED_ACCOUNT_ACCESS", ex.getErrorCode());
        verifyNoInteractions(ledgerService);
    }

    @Test
    @DisplayName("Should process transfer with platform fee and credit system revenue account")
    void shouldTransferWithFeeDeduction() {
        BigDecimal transferAmount = new BigDecimal("100.00");
        BigDecimal fee = new BigDecimal("2.50");

        TransferRequest request = new TransferRequest(
                senderId, recipientId, transferAmount, fee, "USD", "Transfer with fee"
        );

        when(accountRepository.findByIdForUpdate(senderId)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(recipientId)).thenReturn(Optional.of(recipientAccount));
        when(accountRepository.findByAccountNumber("SYS-4001-USD")).thenReturn(Optional.of(feeRevenueAccount));

        when(ledgerService.getAccountBalance(senderId)).thenReturn(new AccountBalanceResponse(
                senderId, "USD-1001", "Sender Wallet", AccountType.LIABILITY, "USD",
                AccountStatus.ACTIVE, new BigDecimal("200.00"), BigDecimal.ZERO, new BigDecimal("200.00")
        ));

        ArgumentCaptor<LedgerPostingRequest> captor = ArgumentCaptor.forClass(LedgerPostingRequest.class);
        when(ledgerService.postTransaction(captor.capture())).thenReturn(new TransactionResponse(
                UUID.randomUUID(), "TX-20261031-999999", "idem-fee", TransactionStatus.COMPLETED,
                "Transfer with fee", OffsetDateTime.now(), OffsetDateTime.now(), Collections.emptyList()
        ));

        TransferResponse response = transferService.transfer(request, "idem-fee", "sender@example.com");

        assertNotNull(response);
        assertEquals(new BigDecimal("2.50"), response.fee());

        LedgerPostingRequest captured = captor.getValue();
        assertEquals(3, captured.entries().size());
        assertEquals(new BigDecimal("102.50"), captured.entries().get(0).amount());
        assertEquals(new BigDecimal("100.00"), captured.entries().get(1).amount());
        assertEquals(new BigDecimal("2.50"), captured.entries().get(2).amount());
    }

    @Test
    @DisplayName("Should reject transfer when sender has insufficient funds")
    void shouldRejectTransferWhenInsufficientFunds() {
        TransferRequest request = new TransferRequest(
                senderId, recipientId, new BigDecimal("150.00"), BigDecimal.ZERO, "USD", "Large transfer"
        );

        when(accountRepository.findByIdForUpdate(senderId)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(recipientId)).thenReturn(Optional.of(recipientAccount));

        when(ledgerService.getAccountBalance(senderId)).thenReturn(new AccountBalanceResponse(
                senderId, "USD-1001", "Sender Wallet", AccountType.LIABILITY, "USD",
                AccountStatus.ACTIVE, new BigDecimal("50.00"), BigDecimal.ZERO, new BigDecimal("50.00")
        ));

        InsufficientFundsException ex = assertThrows(InsufficientFundsException.class, () ->
                transferService.transfer(request, "idem-fail", "sender@example.com")
        );

        assertEquals("INSUFFICIENT_FUNDS", ex.getErrorCode());
        verify(ledgerService, never()).postTransaction(any());
    }

    @Test
    @DisplayName("Should reject self-transfer when sender equals recipient")
    void shouldRejectSelfTransfer() {
        TransferRequest request = new TransferRequest(
                senderId, senderId, new BigDecimal("20.00"), BigDecimal.ZERO, "USD", "Self transfer"
        );

        SelfTransferException ex = assertThrows(SelfTransferException.class, () ->
                transferService.transfer(request, "idem-self", "sender@example.com")
        );

        assertEquals("SELF_TRANSFER_NOT_ALLOWED", ex.getErrorCode());
        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("Should reject currency mismatch")
    void shouldRejectCurrencyMismatch() {
        Account eurRecipient = new Account(null, "EUR-9999", "EUR Wallet", AccountType.LIABILITY, "EUR");
        eurRecipient.setId(recipientId);
        eurRecipient.setStatus(AccountStatus.ACTIVE);

        TransferRequest request = new TransferRequest(
                senderId, recipientId, new BigDecimal("20.00"), BigDecimal.ZERO, "USD", "Mismatch"
        );

        when(accountRepository.findByIdForUpdate(senderId)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(recipientId)).thenReturn(Optional.of(eurRecipient));

        CurrencyMismatchException ex = assertThrows(CurrencyMismatchException.class, () ->
                transferService.transfer(request, "idem-currency", "sender@example.com")
        );

        assertEquals("CURRENCY_MISMATCH", ex.getErrorCode());
    }

    @Test
    @DisplayName("Should reject zero or negative transfer amount")
    void shouldRejectZeroAmount() {
        TransferRequest request = new TransferRequest(
                senderId, recipientId, BigDecimal.ZERO, BigDecimal.ZERO, "USD", "Zero amount"
        );

        assertThrows(InvalidAmountException.class, () ->
                transferService.transfer(request, "idem-zero", "sender@example.com")
        );
    }
}
