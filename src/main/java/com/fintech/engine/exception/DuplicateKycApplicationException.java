package com.fintech.engine.exception;

public class DuplicateKycApplicationException extends FintechException {
    public DuplicateKycApplicationException(String message) {
        super(message, "DUPLICATE_KYC_APPLICATION");
    }
}

