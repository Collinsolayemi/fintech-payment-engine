package com.fintech.engine.service;

import com.fintech.engine.dto.CreatePaymentRequestDto;
import com.fintech.engine.dto.PayPaymentRequestDto;
import com.fintech.engine.dto.PaymentRequestResponse;
import com.fintech.engine.dto.TransferRequest;
import com.fintech.engine.dto.TransferResponse;
import com.fintech.engine.model.entity.Account;
import com.fintech.engine.model.entity.PaymentRequest;
import com.fintech.engine.model.entity.User;
import com.fintech.engine.model.enums.AccountStatus;
import com.fintech.engine.model.enums.AccountType;
import com.fintech.engine.model.enums.PaymentRequestStatus;
import com.fintech.engine.model.enums.TransactionStatus;
import com.fintech.engine.repository.AccountRepository;
import com.fintech.engine.repository.PaymentRequestRepository;
import com.fintech.engine.repository.UserRepository;
import com.fintech.engine.service.impl.PaymentRequestServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRequestServiceTest {

    @Mock
    private PaymentRequestRepository paymentRequestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransferService transferService;

    private PaymentRequestServiceImpl service;

    private User requester;
    private User payer;
    private Account requesterWallet;
    private Account payerWallet;

    @BeforeEach
    void setUp() {
        service = new PaymentRequestServiceImpl(
                paymentRequestRepository,
                userRepository,
                accountRepository,
                transferService
        );

        requester = new User();
        requester.setId(UUID.randomUUID());
        requester.setEmail("alex@example.com");
        requester.setFirstName("Alex");
        requester.setLastName("Morgan");

        payer = new User();
        payer.setId(UUID.randomUUID());
        payer.setEmail("sam@example.com");
        payer.setFirstName("Sam");
        payer.setLastName("Lee");

        requesterWallet = new Account(requester, "USD-11112222", "Alex USD", AccountType.LIABILITY, "USD");
        requesterWallet.setId(UUID.randomUUID());
        requesterWallet.setStatus(AccountStatus.ACTIVE);

        payerWallet = new Account(payer, "USD-33334444", "Sam USD", AccountType.LIABILITY, "USD");
        payerWallet.setId(UUID.randomUUID());
        payerWallet.setStatus(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should create a payment request with a unique REQ- code")
    void create_success() {
        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(requester));
        when(accountRepository.findById(requesterWallet.getId())).thenReturn(Optional.of(requesterWallet));
        when(userRepository.findByEmail("sam@example.com")).thenReturn(Optional.of(payer));
        when(paymentRequestRepository.existsByRequestCode(any())).thenReturn(false);
        when(paymentRequestRepository.save(any(PaymentRequest.class))).thenAnswer(inv -> {
            PaymentRequest pr = inv.getArgument(0);
            pr.setId(UUID.randomUUID());
            return pr;
        });

        PaymentRequestResponse res = service.create(
                "alex@example.com",
                new CreatePaymentRequestDto(
                        requesterWallet.getId(),
                        "sam@example.com",
                        new BigDecimal("125.00"),
                        "Dinner split",
                        null
                )
        );

        assertThat(res.requestCode()).startsWith("REQ-");
        assertThat(res.status()).isEqualTo(PaymentRequestStatus.PENDING);
        assertThat(res.currency()).isEqualTo("USD");
        assertThat(res.amount()).isEqualByComparingTo("125.00");
        assertThat(res.payerUserId()).isEqualTo(payer.getId());
    }

    @Test
    @DisplayName("Should fulfill a pending payment request via TransferService")
    void pay_success() {
        UUID reqId = UUID.randomUUID();
        PaymentRequest pr = new PaymentRequest();
        pr.setId(reqId);
        pr.setRequestCode("REQ-ABCD1234");
        pr.setRequesterUser(requester);
        pr.setRequesterAccount(requesterWallet);
        pr.setPayerUser(payer);
        pr.setPayerEmail(payer.getEmail());
        pr.setAmount(new BigDecimal("125.00"));
        pr.setCurrency("USD");
        pr.setDescription("Dinner split");
        pr.setStatus(PaymentRequestStatus.PENDING);

        when(userRepository.findByEmail("sam@example.com")).thenReturn(Optional.of(payer));
        when(paymentRequestRepository.findById(reqId)).thenReturn(Optional.of(pr));
        when(accountRepository.findById(payerWallet.getId())).thenReturn(Optional.of(payerWallet));
        when(transferService.transfer(any(TransferRequest.class), eq("idem-pay-1"), eq("sam@example.com")))
                .thenReturn(new TransferResponse(
                        "TXN-REQ-001",
                        "idem-pay-1",
                        TransactionStatus.COMPLETED,
                        new BigDecimal("125.00"),
                        BigDecimal.ZERO,
                        "USD",
                        payerWallet.getId(),
                        requesterWallet.getId(),
                        "Payment for request REQ-ABCD1234: Dinner split",
                        OffsetDateTime.now()
                ));
        when(paymentRequestRepository.save(any(PaymentRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentRequestResponse res = service.pay(
                "sam@example.com",
                reqId,
                new PayPaymentRequestDto(payerWallet.getId()),
                "idem-pay-1"
        );

        assertThat(res.status()).isEqualTo(PaymentRequestStatus.PAID);
        assertThat(res.transactionReference()).isEqualTo("TXN-REQ-001");
        assertThat(res.paidAt()).isNotNull();
        verify(transferService).transfer(any(TransferRequest.class), eq("idem-pay-1"), eq("sam@example.com"));
    }

    @Test
    @DisplayName("Should reject paying your own payment request")
    void pay_ownRequestThrows() {
        UUID reqId = UUID.randomUUID();
        PaymentRequest pr = new PaymentRequest();
        pr.setId(reqId);
        pr.setRequestCode("REQ-SELF1234");
        pr.setRequesterUser(requester);
        pr.setRequesterAccount(requesterWallet);
        pr.setAmount(new BigDecimal("50.00"));
        pr.setCurrency("USD");
        pr.setStatus(PaymentRequestStatus.PENDING);

        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(requester));
        when(paymentRequestRepository.findById(reqId)).thenReturn(Optional.of(pr));

        assertThatThrownBy(() -> service.pay(
                "alex@example.com",
                reqId,
                new PayPaymentRequestDto(requesterWallet.getId()),
                null
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cannot pay your own payment request");
    }
}

