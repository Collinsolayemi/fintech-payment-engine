package com.fintech.engine.service;

import com.fintech.engine.dto.*;
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
import com.fintech.engine.service.impl.VirtualAccountServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VirtualAccountServiceTest {

    @Mock
    private VirtualAccountRepository virtualAccountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private LedgerService ledgerService;

    private VirtualAccountServiceImpl virtualAccountService;

    private User testUser;
    private Account usdWallet;

    @BeforeEach
    void setUp() {
        virtualAccountService = new VirtualAccountServiceImpl(
                virtualAccountRepository,
                userRepository,
                accountRepository,
                accountService,
                ledgerService
        );

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("alex@example.com");
        testUser.setFirstName("Alex");
        testUser.setLastName("Morgan");

        usdWallet = new Account(testUser, "USD-11223344", "Alex's USD Wallet", AccountType.LIABILITY, "USD");
        usdWallet.setId(UUID.randomUUID());
        usdWallet.setStatus(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should provision a USD virtual account linked to existing USD wallet")
    void createVirtualAccount_usdSuccess() {
        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(testUser));
        when(virtualAccountRepository.existsByUserIdAndCurrency(testUser.getId(), "USD")).thenReturn(false);
        when(accountRepository.findByUserId(testUser.getId())).thenReturn(List.of(usdWallet));
        when(virtualAccountRepository.existsByAccountNumber(any())).thenReturn(false);
        when(virtualAccountRepository.saveAndFlush(any(VirtualAccount.class))).thenAnswer(inv -> {
            VirtualAccount va = inv.getArgument(0);
            va.setId(UUID.randomUUID());
            return va;
        });

        VirtualAccountResponse response = virtualAccountService.createVirtualAccount(
                "alex@example.com",
                new CreateVirtualAccountRequest("usd", null)
        );

        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.bankName()).isEqualTo("Community Federal Savings Bank");
        assertThat(response.routingNumber()).isEqualTo("026073150");
        assertThat(response.accountName()).isEqualTo("ALEX MORGAN");
        assertThat(response.status()).isEqualTo(VirtualAccountStatus.ACTIVE);
        assertThat(response.accountId()).isEqualTo(usdWallet.getId());
    }

    @Test
    @DisplayName("Should auto-provision GBP wallet when creating GBP virtual account without existing wallet")
    void createVirtualAccount_autoProvisionsWallet() {
        Account gbpWallet = new Account(testUser, "GBP-55667788", "Alex's GBP Wallet", AccountType.LIABILITY, "GBP");
        gbpWallet.setId(UUID.randomUUID());
        gbpWallet.setStatus(AccountStatus.ACTIVE);

        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(testUser));
        when(virtualAccountRepository.existsByUserIdAndCurrency(testUser.getId(), "GBP")).thenReturn(false);
        when(accountRepository.findByUserId(testUser.getId())).thenReturn(List.of(usdWallet));
        when(accountService.createCurrencyWallet(eq("alex@example.com"), any(CreateCurrencyWalletRequest.class)))
                .thenReturn(new AccountBalanceResponse(gbpWallet.getId(), gbpWallet.getAccountNumber(), "Alex's GBP Wallet", AccountType.LIABILITY, "GBP", BigDecimal.ZERO, OffsetDateTime.now()));
        when(accountRepository.findById(gbpWallet.getId())).thenReturn(Optional.of(gbpWallet));
        when(virtualAccountRepository.existsByAccountNumber(any())).thenReturn(false);
        when(virtualAccountRepository.saveAndFlush(any(VirtualAccount.class))).thenAnswer(inv -> {
            VirtualAccount va = inv.getArgument(0);
            va.setId(UUID.randomUUID());
            return va;
        });

        VirtualAccountResponse response = virtualAccountService.createVirtualAccount(
                "alex@example.com",
                new CreateVirtualAccountRequest("GBP", null)
        );

        assertThat(response.currency()).isEqualTo("GBP");
        assertThat(response.sortCode()).isEqualTo("04-00-75");
        assertThat(response.iban()).startsWith("GB29CLRB040075");
        verify(accountService).createCurrencyWallet(eq("alex@example.com"), any(CreateCurrencyWalletRequest.class));
    }

    @Test
    @DisplayName("Should reject duplicate virtual account for same currency")
    void createVirtualAccount_duplicateCurrencyThrows() {
        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(testUser));
        when(virtualAccountRepository.existsByUserIdAndCurrency(testUser.getId(), "USD")).thenReturn(true);

        assertThatThrownBy(() -> virtualAccountService.createVirtualAccount(
                "alex@example.com",
                new CreateVirtualAccountRequest("USD", null)
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already have a USD virtual account");
    }

    @Test
    @DisplayName("Should reject reopening a closed virtual account")
    void updateStatus_closedCannotReopen() {
        UUID vaId = UUID.randomUUID();
        VirtualAccount va = new VirtualAccount(
                testUser, usdWallet, "USD", "4012345678", "ALEX MORGAN",
                "Community Federal Savings Bank", "026073150", null, null, "CFSBUS33",
                VirtualAccountStatus.CLOSED, "SIMULATED"
        );
        va.setId(vaId);

        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(testUser));
        when(virtualAccountRepository.findById(vaId)).thenReturn(Optional.of(va));

        assertThatThrownBy(() -> virtualAccountService.updateStatus("alex@example.com", vaId, VirtualAccountStatus.ACTIVE))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Closed virtual accounts cannot be reopened");
    }

    @Test
    @DisplayName("Should credit linked wallet via double-entry ledger on simulated inbound deposit")
    void simulateInboundDeposit_creditsWallet() {
        UUID vaId = UUID.randomUUID();
        VirtualAccount va = new VirtualAccount(
                testUser, usdWallet, "USD", "4012345678", "ALEX MORGAN",
                "Community Federal Savings Bank", "026073150", null, null, "CFSBUS33",
                VirtualAccountStatus.ACTIVE, "SIMULATED"
        );
        va.setId(vaId);

        Account clearing = new Account(null, "SYS-1002-USD", "GATEWAY_RECEIVABLE_STRIPE", AccountType.ASSET, "USD");
        clearing.setId(UUID.randomUUID());

        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(testUser));
        when(virtualAccountRepository.findById(vaId)).thenReturn(Optional.of(va));
        when(accountRepository.findByAccountNumber("SYS-1002-USD")).thenReturn(Optional.of(clearing));
        when(ledgerService.getAccountBalance(usdWallet.getId())).thenReturn(
                new AccountBalanceResponse(usdWallet.getId(), usdWallet.getAccountNumber(), usdWallet.getAccountName(),
                        AccountType.LIABILITY, "USD", new BigDecimal("500.00"), OffsetDateTime.now())
        );

        VirtualAccountDepositResponse response = virtualAccountService.simulateInboundDeposit(
                "alex@example.com",
                vaId,
                new SimulateVirtualAccountDepositRequest(new BigDecimal("500.00"), "Acme Corp", "Invoice #42"),
                "idem-va-123"
        );

        assertThat(response.amount()).isEqualByComparingTo("500.00");
        assertThat(response.newWalletBalance()).isEqualByComparingTo("500.00");
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.senderName()).isEqualTo("Acme Corp");

        ArgumentCaptor<LedgerPostingRequest> captor = ArgumentCaptor.forClass(LedgerPostingRequest.class);
        verify(ledgerService).postTransaction(captor.capture());
        LedgerPostingRequest posted = captor.getValue();
        assertThat(posted.idempotencyKey()).isEqualTo("idem-va-123");
        assertThat(posted.entries()).hasSize(2);
        assertThat(posted.entries().get(0).entryType()).isEqualTo(EntryType.DEBIT);
        assertThat(posted.entries().get(0).accountId()).isEqualTo(clearing.getId());
        assertThat(posted.entries().get(1).entryType()).isEqualTo(EntryType.CREDIT);
        assertThat(posted.entries().get(1).accountId()).isEqualTo(usdWallet.getId());
    }
}

