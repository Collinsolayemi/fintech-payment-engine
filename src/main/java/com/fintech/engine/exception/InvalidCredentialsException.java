package com.fintech.engine.exception;

public class InvalidCredentialsException extends FintechException {
    public InvalidCredentialsException() {
        super("Invalid email or password", "INVALID_CREDENTIALS");
    }
}

