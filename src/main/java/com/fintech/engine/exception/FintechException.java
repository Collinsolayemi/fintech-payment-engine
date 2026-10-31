package com.fintech.engine.exception;

public abstract class FintechException extends RuntimeException {
    private final String errorCode;

    public FintechException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
