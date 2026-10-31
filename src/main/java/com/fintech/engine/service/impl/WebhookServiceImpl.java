package com.fintech.engine.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.engine.dto.AccountBalanceResponse;
import com.fintech.engine.dto.LedgerEntryCommand;
import com.fintech.engine.dto.LedgerPostingRequest;
import com.fintech.engine.dto.WebhookResponse;
import com.fintech.engine.exception.AccountNotFoundException;
import com.fintech.engine.exception.InvalidWebhookSignatureException;
import com.fintech.engine.model.entity.Account;
import com.fintech.engine.model.entity.User;
import com.fintech.engine.model.entity.WebhookEvent;
import com.fintech.engine.model.enums.EntryType;
import com.fintech.engine.model.enums.WebhookProvider;
import com.fintech.engine.model.enums.WebhookStatus;
import com.fintech.engine.repository.AccountRepository;
import com.fintech.engine.repository.UserRepository;
import com.fintech.engine.repository.WebhookEventRepository;
import com.fintech.engine.security.WebhookSignatureVerifier;
import com.fintech.engine.service.AccountService;
import com.fintech.engine.service.LedgerService;
import com.fintech.engine.service.WebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
public class WebhookServiceImpl implements WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookServiceImpl.class);

    private final WebhookEventRepository webhookEventRepository;
    private final WebhookSignatureVerifier signatureVerifier;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final ObjectMapper objectMapper;

    @Value("${fintech.webhooks.stripe.secret:whsec_test_stripe_secret_key_12345}")
    private String stripeSecret;

    @Value("${fintech.webhooks.paystack.secret:whsec_test_paystack_secret_key_12345}")
    private String paystackSecret;

    @Value("${fintech.webhooks.flutterwave.secret:whsec_test_flutterwave_secret_key_12345}")
    private String flutterwaveSecret;

    public WebhookServiceImpl(
        WebhookEventRepository webhookEventRepository,
        WebhookSignatureVerifier signatureVerifier,
        AccountRepository accountRepository,
        UserRepository userRepository,
        AccountService accountService,
        LedgerService ledgerService,
        ObjectMapper objectMapper
    ) {
        this.webhookEventRepository = webhookEventRepository;
        this.signatureVerifier = signatureVerifier;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.accountService = accountService;
        this.ledgerService = ledgerService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public WebhookResponse processWebhook(WebhookProvider provider, String signature, String rawPayload) {
        log.info("Ingesting inbound webhook from provider: {}", provider);

        // 1. Verify HMAC Signature
        String secretKey = getSecretKey(provider);
        byte[] payloadBytes = rawPayload != null ? rawPayload.getBytes(StandardCharsets.UTF_8) : new byte[0];

        if (!signatureVerifier.verifyHmacSha256(payloadBytes, signature, secretKey)) {
            log.warn("Webhook rejected: Invalid signature for provider: {}", provider);
            throw new InvalidWebhookSignatureException();
        }

        // 2. Parse payload JSON
        JsonNode root;
        try {
            root = objectMapper.readTree(rawPayload);
        } catch (Exception e) {
            log.error("Failed to parse webhook JSON payload", e);
            throw new IllegalArgumentException("Malformed webhook JSON payload");
        }

        String eventId = extractEventId(root);
        String eventType = extractEventType(root);

        // 3. De-duplication check (Idempotent Event Ingestion)
        if (webhookEventRepository.existsByProviderAndEventId(provider, eventId)) {
            log.info("Webhook event {} from {} already processed. Returning cached success.", eventId, provider);
            return new WebhookResponse(true, "Duplicate webhook event already processed", eventId, WebhookStatus.DUPLICATE);
        }

        WebhookEvent event = new WebhookEvent(provider, eventId, eventType, rawPayload);
        event = webhookEventRepository.save(event);

        // 4. Handle Successful Deposit Events
        if (isPaymentSuccessEvent(eventType)) {
            processPaymentSuccess(provider, eventId, root);
            event.markProcessed();
            webhookEventRepository.save(event);
            return new WebhookResponse(true, "Payment webhook processed and funds credited", eventId, WebhookStatus.PROCESSED);
        }

        event.setStatus(WebhookStatus.IGNORED);
        webhookEventRepository.save(event);
        return new WebhookResponse(true, "Webhook event recorded (ignored event type)", eventId, WebhookStatus.IGNORED);
    }

    private void processPaymentSuccess(WebhookProvider provider, String eventId, JsonNode root) {
        JsonNode data = root.has("data") ? root.get("data") : root;

        // Extract amount & currency
        BigDecimal amount = BigDecimal.ZERO;
        if (data.has("amount")) {
            amount = new BigDecimal(data.get("amount").asText());
        }

        final String currency = data.has("currency") ? data.get("currency").asText().toUpperCase() : "USD";

        // Locate destination account
        Account destinationAccount = resolveDestinationAccount(data);

        // Locate platform gateway clearing asset account (e.g. SYS-1002-USD)
        String gatewayAccountNumber = "SYS-1002-" + currency;
        Account gatewayAccount = accountRepository.findByAccountNumber(gatewayAccountNumber)
                .orElseGet(() -> {
                    // Fallback to primary platform settlement bank if specific gateway account not found
                    return accountRepository.findByAccountNumber("SYS-1001-" + currency)
                            .orElseThrow(() -> new AccountNotFoundException("Gateway clearing account not found for: " + currency));
                });

        // 5. Execute Double-Entry Balanced Journal Posting
        String postingRef = "DEP-" + provider.name() + "-" + eventId;
        LedgerPostingRequest postingRequest = new LedgerPostingRequest(
                postingRef,
                "Inbound deposit via " + provider.name() + " event: " + eventId,
                eventId,
                List.of(
                        new LedgerEntryCommand(gatewayAccount.getId(), EntryType.DEBIT, amount),
                        new LedgerEntryCommand(destinationAccount.getId(), EntryType.CREDIT, amount)
                )
        );

        ledgerService.postTransaction(postingRequest);
        log.info("Successfully credited {} {} to wallet {} via webhook {}", amount, currency, destinationAccount.getAccountNumber(), eventId);
    }

    private Account resolveDestinationAccount(JsonNode data) {
        // Option A: accountId explicitly in data or metadata
        if (data.has("accountId")) {
            UUID accId = UUID.fromString(data.get("accountId").asText());
            return accountRepository.findById(accId)
                    .orElseThrow(() -> new AccountNotFoundException(accId));
        }

        if (data.has("metadata") && data.get("metadata").has("accountId")) {
            UUID accId = UUID.fromString(data.get("metadata").get("accountId").asText());
            return accountRepository.findById(accId)
                    .orElseThrow(() -> new AccountNotFoundException(accId));
        }

        // Option B: customer email
        String resolvedEmail = null;
        if (data.has("customer_email")) {
            resolvedEmail = data.get("customer_email").asText();
        } else if (data.has("email")) {
            resolvedEmail = data.get("email").asText();
        } else if (data.has("metadata") && data.get("metadata").has("customer_email")) {
            resolvedEmail = data.get("metadata").get("customer_email").asText();
        }

        if (resolvedEmail != null) {
            final String email = resolvedEmail;
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new AccountNotFoundException("No user found with email: " + email));
            List<AccountBalanceResponse> accounts = accountService.getUserAccounts(user.getId());
            if (!accounts.isEmpty()) {
                return accountRepository.findById(accounts.get(0).accountId())
                        .orElseThrow(() -> new AccountNotFoundException("User has no primary account"));
            }
        }

        throw new AccountNotFoundException("Could not resolve target wallet from webhook metadata");
    }

    private boolean isPaymentSuccessEvent(String eventType) {
        if (eventType == null) return false;
        String lower = eventType.toLowerCase();
        return lower.contains("succeeded") || lower.contains("success") || lower.contains("paid");
    }

    private String extractEventId(JsonNode root) {
        if (root.has("id")) return root.get("id").asText();
        if (root.has("event_id")) return root.get("event_id").asText();
        if (root.has("reference")) return root.get("reference").asText();
        return UUID.randomUUID().toString();
    }

    private String extractEventType(JsonNode root) {
        if (root.has("type")) return root.get("type").asText();
        if (root.has("event")) return root.get("event").asText();
        return "payment.unknown";
    }

    private String getSecretKey(WebhookProvider provider) {
        return switch (provider) {
            case STRIPE -> stripeSecret;
            case PAYSTACK -> paystackSecret;
            case FLUTTERWAVE -> flutterwaveSecret;
        };
    }
}
