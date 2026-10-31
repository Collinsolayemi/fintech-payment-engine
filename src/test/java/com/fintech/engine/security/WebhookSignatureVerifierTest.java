package com.fintech.engine.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookSignatureVerifierTest {

    private WebhookSignatureVerifier verifier;
    private static final String SECRET = "test_webhook_secret_key_998877";

    @BeforeEach
    void setUp() {
        verifier = new WebhookSignatureVerifier();
    }

    @Test
    @DisplayName("Should accept valid HMAC-SHA256 signature")
    void shouldAcceptValidSignature() {
        String payload = "{\"event\":\"payment.succeeded\",\"amount\":150.00}";
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        String signature = verifier.computeHmacSha256(payloadBytes, SECRET);

        boolean isValid = verifier.verifyHmacSha256(payloadBytes, signature, SECRET);
        assertTrue(isValid);
    }

    @Test
    @DisplayName("Should reject tampered payload or signature")
    void shouldRejectTamperedSignature() {
        String payload = "{\"event\":\"payment.succeeded\",\"amount\":150.00}";
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

        boolean isValid = verifier.verifyHmacSha256(payloadBytes, "bad_signature_hash", SECRET);
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should parse and accept Stripe-style formatted signature header (t=...,v1=...)")
    void shouldAcceptStripeFormattedSignature() {
        String payload = "{\"id\":\"evt_123\",\"type\":\"charge.success\"}";
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        String hash = verifier.computeHmacSha256(payloadBytes, SECRET);
        String stripeHeader = "t=1614555000,v1=" + hash;

        boolean isValid = verifier.verifyHmacSha256(payloadBytes, stripeHeader, SECRET);
        assertTrue(isValid);
    }
}

