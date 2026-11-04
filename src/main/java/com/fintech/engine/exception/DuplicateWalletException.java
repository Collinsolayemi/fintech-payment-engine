package com.fintech.engine.exception;

public class DuplicateWalletException extends FintechException {
    public DuplicateWalletException(String currency) {
        super("User already possesses an active wallet in currency: " + currency, "DUPLICATE_WALLET");
    }
}

