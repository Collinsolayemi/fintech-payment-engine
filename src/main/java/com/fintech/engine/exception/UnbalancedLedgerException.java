package com.fintech.engine.exception;

import java.math.BigDecimal;

public class UnbalancedLedgerException extends FintechException {
    public UnbalancedLedgerException(BigDecimal totalDebits, BigDecimal totalCredits) {
        super(String.format("Unbalanced ledger entries: Total Debits (%s) does not equal Total Credits (%s)",
                totalDebits.toPlainString(), totalCredits.toPlainString()), "UNBALANCED_LEDGER");
    }
}

