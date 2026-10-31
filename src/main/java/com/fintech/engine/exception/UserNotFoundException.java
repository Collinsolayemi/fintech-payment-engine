package com.fintech.engine.exception;

public class UserNotFoundException extends FintechException {
    public UserNotFoundException(String message) {
        super(message, "USER_NOT_FOUND");
    }
}

