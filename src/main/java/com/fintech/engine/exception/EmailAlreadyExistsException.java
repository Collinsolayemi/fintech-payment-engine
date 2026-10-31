package com.fintech.engine.exception;

public class EmailAlreadyExistsException extends FintechException {
    public EmailAlreadyExistsException(String email) {
        super("An account with email '" + email + "' already exists", "EMAIL_ALREADY_EXISTS");
    }
}

