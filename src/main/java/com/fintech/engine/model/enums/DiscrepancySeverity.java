package com.fintech.engine.model.enums;

public enum DiscrepancySeverity {
    /** Books are mathematically broken or money is unaccounted for. Page on-call immediately. */
    CRITICAL,
    /** A money movement is missing or inconsistent with its business record. */
    HIGH,
    /** Data quality issue that does not by itself imply lost funds. */
    MEDIUM
}

