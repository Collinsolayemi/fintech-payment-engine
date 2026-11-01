package com.fintech.engine.model.enums;

public enum ReconciliationStatus {
    RUNNING,
    /** All checks completed with zero discrepancies. */
    PASSED,
    /** All checks completed and at least one discrepancy was detected. */
    FAILED,
    /** The run aborted due to an unexpected technical error (results incomplete). */
    ERROR
}

