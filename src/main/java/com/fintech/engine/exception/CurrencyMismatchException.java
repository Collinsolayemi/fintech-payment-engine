package com.fintech.engine.exception;

public class CurrencyMismatchException extends FintechException {
    public CurrencyMismatchException(String senderCurrency, String recipientCurrency) {
        super(String.format("Currency mismatch between sender (%s) and recipient (%s). Multi-currency transfers require FX conversion.",
                senderCurrency, recipientCurrency), "CURRENCY_MISMATCH");
    }
}

