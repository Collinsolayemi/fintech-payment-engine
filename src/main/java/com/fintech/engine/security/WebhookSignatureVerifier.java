package com.fintech.engine.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class WebhookSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(WebhookSignatureVerifier.class);

    public boolean verifyHmacSha256(byte[] payload, String receivedSignature, String secretKey) {
        if (receivedSignature == null || receivedSignature.isBlank() || secretKey == null || secretKey.isBlank()) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] expectedHash = mac.doFinal(payload);
            String expectedSignature = HexFormat.of().formatHex(expectedHash);

            // Strip prefix if header comes as "t=...,v1=..." (Stripe style) or "sha256=..."
            String cleanReceived = receivedSignature;
            if (cleanReceived.contains("v1=")) {
                for (String part : cleanReceived.split(",")) {
                    if (part.trim().startsWith("v1=")) {
                        cleanReceived = part.trim().substring(3);
                        break;
                    }
                }
            } else if (cleanReceived.startsWith("sha256=")) {
                cleanReceived = cleanReceived.substring(7);
            }

            return MessageDigest.isEqual(
                    cleanReceived.toLowerCase().getBytes(StandardCharsets.UTF_8),
                    expectedSignature.toLowerCase().getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Failed to compute and verify webhook signature: {}", e.getMessage());
            return false;
        }
    }

    public String computeHmacSha256(byte[] payload, String secretKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Could not compute HMAC-SHA256 signature", e);
        }
    }
}

