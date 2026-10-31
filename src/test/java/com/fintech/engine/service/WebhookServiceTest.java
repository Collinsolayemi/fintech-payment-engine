package com.fintech.engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.engine.dto.LedgerPostingRequest;
import com.fintech.engine.dto.TransactionResponse;
import com.fintech.engine.dto.WebhookResponse;
import com.fintech.engine.exception.InvalidWebhookSignatureException;
import com.fintech.engine.model.entity.Account;
import com.fintech.engine.model.entity.WebhookEvent;
import com.fintech.engine.model.enums.AccountType;
import com.fintech.engine.model.enums.TransactionStatus;
import com.fintech.engine.model.enums.WebhookProvider;
import com.fintech.engine.model.enums.WebhookStatus;
import com.fintech.engine.repository.AccountRepository;
import com.fintech.engine.repository.UserRepository;
import com.fintech.engine.repository.WebhookEventRepository;
import com.fintech.engine.security.WebhookSignatureVerifier;
import com.fintech.engine.service.impl.WebhookServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private LedgerService ledgerService;

    private final WebhookSignatureVerifier signatureVerifier = new WebhookSignatureVerifier();
    private WebhookServiceImpl webhookService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String STRIPE_SECRET = "whsec_stripe_test";

    @BeforeEach
    void setUp() {
        webhookService = new WebhookServiceImpl(
                webhookEventRepository,
                signatureVerifier,
                accountRepository,
                userRepository,
                accountService,
                ledgerService,
                objectMapper
        );
        ReflectionTestUtils.setField(webhookService, "stripeSecret", STRIPE_SECRET);
    }

    @Test
    @DisplayName("Should process valid inbound deposit webhook and post balanced ledger entries")
    void shouldProcessValidDepositWebhook() {
        UUID customerAccountId = UUID.randomUUID();
        UUID gatewayAccountId = UUID.randomUUID();

        String rawPayload = "{\"id\":\"evt_deposit_99\",\"type\":\"payment.succeeded\",\"data\":{\"amount\":\"250.00\",\"currency\":\"USD\",\"accountId\":\"" + customerAccountId + "\"}}";
        String signature = signatureVerifier.computeHmacSha256(rawPayload.getBytes(StandardCharsets.UTF_8), STRIPE_SECRET);

        when(webhookEventRepository.existsByProviderAndEventId(WebhookProvider.STRIPE, "evt_deposit_99")).thenReturn(false);

        Account customerAccount = new Account(null, "USD-11223344", "Customer Wallet", AccountType.LIABILITY, "USD");
        customerAccount.setId(customerAccountId);
        when(accountRepository.findById(customerAccountId)).thenReturn(Optional.of(customerAccount));

        Account gatewayAccount = new Account(null, "SYS-1002-USD", "Stripe Clearing", AccountType.ASSET, "USD");
        gatewayAccount.setId(gatewayAccountId);
        when(accountRepository.findByAccountNumber("SYS-1002-USD")).thenReturn(Optional.of(gatewayAccount));

        when(webhookEventRepository.save(any(WebhookEvent.class))).thenAnswer(i -> i.getArgument(0));

        TransactionResponse dummyTx = new TransactionResponse(
                UUID.randomUUID(), "DEP-STRIPE-evt_deposit_99", "evt_deposit_99",
                TransactionStatus.COMPLETED, "desc", OffsetDateTime.now(), OffsetDateTime.now(), List.of()
        );
        when(ledgerService.postTransaction(any(LedgerPostingRequest.class))).thenReturn(dummyTx);

        WebhookResponse response = webhookService.processWebhook(WebhookProvider.STRIPE, signature, rawPayload);

        assertNotNull(response);
        assertTrue(response.success());
        assertEquals(WebhookStatus.PROCESSED, response.status());
        assertEquals("evt_deposit_99", response.eventId());

        verify(ledgerService).postTransaction(any(LedgerPostingRequest.class));
    }

    @Test
    @DisplayName("Should detect duplicate webhook and return DUPLICATE without double posting")
    void shouldDetectDuplicateWebhook() {
        String rawPayload = "{\"id\":\"evt_deposit_99\",\"type\":\"payment.succeeded\"}";
        String signature = signatureVerifier.computeHmacSha256(rawPayload.getBytes(StandardCharsets.UTF_8), STRIPE_SECRET);

        when(webhookEventRepository.existsByProviderAndEventId(WebhookProvider.STRIPE, "evt_deposit_99")).thenReturn(true);

        WebhookResponse response = webhookService.processWebhook(WebhookProvider.STRIPE, signature, rawPayload);

        assertNotNull(response);
        assertTrue(response.success());
        assertEquals(WebhookStatus.DUPLICATE, response.status());
        verifyNoInteractions(ledgerService);
    }

    @Test
    @DisplayName("Should throw InvalidWebhookSignatureException on tampered signature")
    void shouldRejectInvalidSignature() {
        String rawPayload = "{\"id\":\"evt_tampered\"}";

        assertThrows(InvalidWebhookSignatureException.class, () ->
                webhookService.processWebhook(WebhookProvider.STRIPE, "bad_sig", rawPayload));
        verifyNoInteractions(ledgerService);
    }
}
