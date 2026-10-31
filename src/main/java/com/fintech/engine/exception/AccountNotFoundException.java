package com.fintech.engine.exception;

import java.util.UUID;

public class AccountNotFoundException extends FintechException {
    public AccountNotFoundException(UUID accountId) {
        super("Account not found with ID: " + accountId, "ACCOUNT_NOT_FOUND");
    }

    public AccountNotFoundException(String accountNumber) {
        super("Account not found with account number: " + accountNumber, "ACCOUNT_NOT_FOUND");
    }
}

