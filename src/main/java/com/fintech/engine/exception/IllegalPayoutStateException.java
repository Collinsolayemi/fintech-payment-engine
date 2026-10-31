package com.fintech.engine.exception;

public class IllegalPayoutStateException extends FintechException {
    public IllegalPayoutStateException(String message) {
        super(message, "ILLEGAL_PAYOUT_STATE");
    }
}

