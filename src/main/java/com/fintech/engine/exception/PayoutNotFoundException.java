package com.fintech.engine.exception;

public class PayoutNotFoundException extends FintechException {
    public PayoutNotFoundException(String reference) {
        super("Payout not found for reference: " + reference, "PAYOUT_NOT_FOUND");
    }
}

