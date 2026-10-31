package com.fintech.engine.exception;

import java.util.UUID;

public class UnauthorizedAccountAccessException extends FintechException {
    public UnauthorizedAccountAccessException(UUID accountId, String userEmail) {
        super(String.format("User '%s' is not authorized to access or debit account '%s'", userEmail, accountId),
                "UNAUTHORIZED_ACCOUNT_ACCESS");
    }
}

