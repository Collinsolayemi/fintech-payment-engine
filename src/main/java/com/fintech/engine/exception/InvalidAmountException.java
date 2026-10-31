package com.fintech.engine.exception;

import java.math.BigDecimal;

public class InvalidAmountException extends FintechException {
    public InvalidAmountException(BigDecimal amount) {
        super("Monetary amount must be strictly positive (> 0), received: " +
                (amount != null ? amount.toPlainString() : "null"), "INVALID_AMOUNT");
    }
}

