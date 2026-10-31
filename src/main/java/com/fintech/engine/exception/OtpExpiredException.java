package com.fintech.engine.exception;

public class OtpExpiredException extends FintechException {
    public OtpExpiredException() {
        super("OTP has expired. Please request a new one.", "OTP_EXPIRED");
    }
}

