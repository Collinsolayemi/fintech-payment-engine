package com.fintech.engine.model.enums;

public enum AccountType {
    ASSET,
    LIABILITY,
    EQUITY,
    REVENUE,
    EXPENSE;

    /**
     * In accounting:
     * - ASSET and EXPENSE accounts have a normal DEBIT balance (Debits increase, Credits decrease).
     * - LIABILITY, EQUITY, and REVENUE accounts have a normal CREDIT balance (Credits increase, Debits decrease).
     */
    public boolean isDebitNormal() {
        return this == ASSET || this == EXPENSE;
    }
}

