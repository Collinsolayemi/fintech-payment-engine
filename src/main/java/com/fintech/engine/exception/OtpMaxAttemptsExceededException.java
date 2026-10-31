package com.fintech.engine.exception;

public class OtpMaxAttemptsExceededException extends FintechException {
    public OtpMaxAttemptsExceededException() {
        super("Maximum OTP verification attempts exceeded. Please request a new OTP.", "MAX_OTP_ATTEMPTS_EXCEEDED");
    }
}

