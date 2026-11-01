package com.fintech.engine.model.enums;

public enum ReconciliationType {
    /** Internal books integrity: trial balance, per-transaction balance, escrow & posting consistency. */
    INTERNAL_INTEGRITY,
    /** External matching of a provider settlement report against ledger deposit postings. */
    GATEWAY_SETTLEMENT
}

