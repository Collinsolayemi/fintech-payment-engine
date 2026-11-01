package com.fintech.engine.exception;

public class InvalidReconciliationRequestException extends FintechException {
    public InvalidReconciliationRequestException(String message) {
        super(message, "INVALID_RECONCILIATION_REQUEST");
    }
}

