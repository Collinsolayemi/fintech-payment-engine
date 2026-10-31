package com.fintech.engine.exception;

public class InvalidRefreshTokenException extends FintechException {
    public InvalidRefreshTokenException(String message) {
        super(message, "INVALID_REFRESH_TOKEN");
    }
}

