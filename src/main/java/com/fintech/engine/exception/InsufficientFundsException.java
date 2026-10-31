package com.fintech.engine.exception;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientFundsException extends FintechException {
    public InsufficientFundsException(UUID accountId, BigDecimal available, BigDecimal requested) {
        super(String.format("Account %s has insufficient funds: Available (%s), Requested (%s)",
                accountId, available.toPlainString(), requested.toPlainString()), "INSUFFICIENT_FUNDS");
    }
}

