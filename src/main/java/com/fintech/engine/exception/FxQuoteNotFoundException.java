package com.fintech.engine.exception;

import java.util.UUID;

public class FxQuoteNotFoundException extends FintechException {
    public FxQuoteNotFoundException(UUID id) {
        super("FX quote not found or expired with ID: " + id, "FX_QUOTE_NOT_FOUND");
    }
}

