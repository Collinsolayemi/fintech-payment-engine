package com.fintech.engine.model.enums;

public enum DiscrepancyType {
    // ---- Internal integrity checks ----
    /** Platform-wide SUM(debits) != SUM(credits). */
    GLOBAL_TRIAL_BALANCE_MISMATCH(DiscrepancySeverity.CRITICAL),
    /** SUM(debits) != SUM(credits) within a single currency (cross-currency leakage). */
    CURRENCY_TRIAL_BALANCE_MISMATCH(DiscrepancySeverity.CRITICAL),
    /** A single journal transaction whose entries do not balance. */
    UNBALANCED_TRANSACTION(DiscrepancySeverity.CRITICAL),
    /** A customer wallet whose ledger-derived balance is below zero (double-spend / overdraft). */
    NEGATIVE_WALLET_BALANCE(DiscrepancySeverity.CRITICAL),
    /** PENDING_SETTLEMENTS escrow balance differs from the sum of in-flight payouts. */
    ESCROW_BALANCE_MISMATCH(DiscrepancySeverity.CRITICAL),
    /** A payout is missing its ESCROW-/SETTLE-/REV- journal posting. */
    MISSING_PAYOUT_POSTING(DiscrepancySeverity.HIGH),
    /** A webhook marked PROCESSED has no corresponding DEP- journal posting. */
    MISSING_DEPOSIT_POSTING(DiscrepancySeverity.HIGH),

    // ---- Gateway settlement checks ----
    /** Present in the provider settlement report, missing in the ledger. */
    MISSING_IN_LEDGER(DiscrepancySeverity.HIGH),
    /** Booked as a deposit in the ledger, missing from the provider settlement report. */
    GHOST_IN_LEDGER(DiscrepancySeverity.CRITICAL),
    /** Ledger amount differs from the provider settled amount. */
    AMOUNT_MISMATCH(DiscrepancySeverity.CRITICAL),
    /** Ledger currency differs from the provider settled currency. */
    CURRENCY_MISMATCH(DiscrepancySeverity.HIGH),
    /** The same reference appears more than once in a provider settlement report. */
    DUPLICATE_SETTLEMENT_RECORD(DiscrepancySeverity.MEDIUM);

    private final DiscrepancySeverity defaultSeverity;

    DiscrepancyType(DiscrepancySeverity defaultSeverity) {
        this.defaultSeverity = defaultSeverity;
    }

    public DiscrepancySeverity getDefaultSeverity() {
        return defaultSeverity;
    }
}

