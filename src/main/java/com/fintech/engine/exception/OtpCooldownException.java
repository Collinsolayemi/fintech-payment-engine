package com.fintech.engine.exception;

public class OtpCooldownException extends FintechException {
    public OtpCooldownException(long remainingSeconds) {
        super("Please wait " + remainingSeconds + " seconds before requesting another OTP", "OTP_COOLDOWN_ACTIVE");
    }
}

