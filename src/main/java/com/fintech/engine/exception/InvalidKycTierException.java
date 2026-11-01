package com.fintech.engine.exception;

public class InvalidKycTierException extends FintechException {
    public InvalidKycTierException(String message) {
        super(message, "INVALID_KYC_TIER");
    }
}

