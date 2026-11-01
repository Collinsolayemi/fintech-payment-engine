package com.fintech.engine.exception;

import java.util.UUID;

public class KycApplicationNotFoundException extends FintechException {
    public KycApplicationNotFoundException(UUID id) {
        super("KYC application not found with ID: " + id, "KYC_APPLICATION_NOT_FOUND");
    }
}

