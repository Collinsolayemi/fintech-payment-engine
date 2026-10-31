package com.fintech.engine.exception;

public class IdempotencyConflictException extends FintechException {
    public IdempotencyConflictException(String key) {
        super("An operation with Idempotency-Key '" + key + "' is currently in progress. Please retry shortly.",
                "IDEMPOTENCY_IN_FLIGHT");
    }
}

