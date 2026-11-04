package com.fintech.engine.exception;

import java.util.UUID;

public class FxQuoteExpiredException extends FintechException {
    public FxQuoteExpiredException(UUID id) {
        super("FX quote " + id + " has expired. Please request a fresh quote.", "FX_QUOTE_EXPIRED");
    }
}

