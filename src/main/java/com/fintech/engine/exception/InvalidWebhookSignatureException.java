package com.fintech.engine.exception;

public class InvalidWebhookSignatureException extends FintechException {
    public InvalidWebhookSignatureException() {
        super("Invalid or tampered webhook signature", "INVALID_WEBHOOK_SIGNATURE");
    }
}

