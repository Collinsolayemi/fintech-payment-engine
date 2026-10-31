package com.fintech.engine.exception;

public class InvalidOtpException extends FintechException {
    public InvalidOtpException(String message) {
        super(message, "INVALID_OTP");
    }
}

