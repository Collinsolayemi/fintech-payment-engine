package com.fintech.engine.exception;

import java.util.UUID;

public class TransactionNotFoundException extends FintechException {
    public TransactionNotFoundException(String reference) {
        super("Transaction not found with reference: " + reference, "TRANSACTION_NOT_FOUND");
    }

    public TransactionNotFoundException(UUID id) {
        super("Transaction not found with ID: " + id, "TRANSACTION_NOT_FOUND");
    }
}

