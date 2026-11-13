package com.fintech.engine.service;

import com.fintech.engine.dto.CreateRecurringTransferRequest;
import com.fintech.engine.dto.RecurringTransferResponse;
import com.fintech.engine.dto.TransferRequest;
import com.fintech.engine.dto.TransferResponse;
import com.fintech.engine.exception.CurrencyMismatchException;
import com.fintech.engine.exception.SelfTransferException;
import com.fintech.engine.model.entity.Account;
import com.fintech.engine.model.entity.RecurringTransfer;
import com.fintech.engine.model.entity.User;
import com.fintech.engine.model.enums.*;
import com.fintech.engine.repository.AccountRepository;
import com.fintech.engine.repository.RecurringTransferRepository;
import com.fintech.engine.repository.UserRepository;
import com.fintech.engine.service.impl.RecurringTransferServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringTransferServiceTest {

    @Mock
    private RecurringTransferRepository recurringTransferRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransferService transferService;

    private RecurringTransferServiceImpl service;

    private User senderUser;
    private User recipientUser;
    private Account senderWallet;
    private Account recipientWallet;

    @BeforeEach
    void setUp() {
        service = new RecurringTransferServiceImpl(
                recurringTransferRepository,
                userRepository,
                accountRepository,
                transferService
        );

        senderUser = new User();
        senderUser.setId(UUID.randomUUID());
        senderUser.setEmail("alex@example.com");

        recipientUser = new User();
        recipientUser.setId(UUID.randomUUID());
        recipientUser.setEmail("sam@example.com");

        senderWallet = new Account(senderUser, "USD-11112222", "Alex USD", AccountType.LIABILITY, "USD");
        senderWallet.setId(UUID.randomUUID());
        senderWallet.setStatus(AccountStatus.ACTIVE);

        recipientWallet = new Account(recipientUser, "USD-33334444", "Sam USD", AccountType.LIABILITY, "USD");
        recipientWallet.setId(UUID.randomUUID());
        recipientWallet.setStatus(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should create a weekly recurring transfer")
    void create_success() {
        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(senderUser));
        when(accountRepository.findById(senderWallet.getId())).thenReturn(Optional.of(senderWallet));
        when(accountRepository.findById(recipientWallet.getId())).thenReturn(Optional.of(recipientWallet));
        when(recurringTransferRepository.save(any(RecurringTransfer.class))).thenAnswer(inv -> {
            RecurringTransfer rt = inv.getArgument(0);
            rt.setId(UUID.randomUUID());
            return rt;
        });

        RecurringTransferResponse res = service.create(
                "alex@example.com",
                new CreateRecurringTransferRequest(
                        senderWallet.getId(),
                        recipientWallet.getId(),
                        new BigDecimal("100.00"),
                        RecurrenceFrequency.WEEKLY,
                        "Weekly allowance",
                        null,
                        null
                )
        );

        assertThat(res.frequency()).isEqualTo(RecurrenceFrequency.WEEKLY);
        assertThat(res.status()).isEqualTo(RecurringTransferStatus.ACTIVE);
        assertThat(res.currency()).isEqualTo("USD");
        assertThat(res.amount()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Should reject self-transfer standing order")
    void create_selfTransferThrows() {
        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(senderUser));

        assertThatThrownBy(() -> service.create(
                "alex@example.com",
                new CreateRecurringTransferRequest(
                        senderWallet.getId(),
                        senderWallet.getId(),
                        new BigDecimal("50.00"),
                        RecurrenceFrequency.MONTHLY,
                        null,
                        null,
                        null
                )
        )).isInstanceOf(SelfTransferException.class);
    }

    @Test
    @DisplayName("Should reject currency mismatch between sender and recipient")
    void create_currencyMismatchThrows() {
        Account eurRecipient = new Account(recipientUser, "EUR-55556666", "Sam EUR", AccountType.LIABILITY, "EUR");
        eurRecipient.setId(UUID.randomUUID());
        eurRecipient.setStatus(AccountStatus.ACTIVE);

        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(senderUser));
        when(accountRepository.findById(senderWallet.getId())).thenReturn(Optional.of(senderWallet));
        when(accountRepository.findById(eurRecipient.getId())).thenReturn(Optional.of(eurRecipient));

        assertThatThrownBy(() -> service.create(
                "alex@example.com",
                new CreateRecurringTransferRequest(
                        senderWallet.getId(),
                        eurRecipient.getId(),
                        new BigDecimal("50.00"),
                        RecurrenceFrequency.DAILY,
                        null,
                        null,
                        null
                )
        )).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    @DisplayName("Should execute due recurring transfer and advance nextRunAt")
    void processDueTransfers_executesAndAdvancesSchedule() {
        RecurringTransfer rt = new RecurringTransfer();
        rt.setId(UUID.randomUUID());
        rt.setUser(senderUser);
        rt.setSenderAccount(senderWallet);
        rt.setRecipientAccount(recipientWallet);
        rt.setAmount(new BigDecimal("75.00"));
        rt.setCurrency("USD");
        rt.setDescription("Rent split");
        rt.setFrequency(RecurrenceFrequency.MONTHLY);
        rt.setStatus(RecurringTransferStatus.ACTIVE);
        rt.setNextRunAt(OffsetDateTime.now().minusMinutes(5));

        when(recurringTransferRepository.findByStatusAndNextRunAtLessThanEqual(eq(RecurringTransferStatus.ACTIVE), any()))
                .thenReturn(List.of(rt));
        when(transferService.transfer(any(TransferRequest.class), eq("REC-" + rt.getId() + "-1"), eq("alex@example.com")))
                .thenReturn(new TransferResponse(
                        "TXN-REC-001",
                        "REC-" + rt.getId() + "-1",
                        TransactionStatus.COMPLETED,
                        new BigDecimal("75.00"),
                        BigDecimal.ZERO,
                        "USD",
                        senderWallet.getId(),
                        recipientWallet.getId(),
                        "Rent split",
                        OffsetDateTime.now()
                ));

        service.processDueTransfers();

        assertThat(rt.getExecutionCount()).isEqualTo(1);
        assertThat(rt.getLastTransactionReference()).isEqualTo("TXN-REC-001");
        assertThat(rt.getNextRunAt()).isAfter(OffsetDateTime.now().plusDays(25));
        verify(recurringTransferRepository).save(rt);
    }
}

