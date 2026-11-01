package com.fintech.engine.exception;

import java.util.UUID;

public class ReconciliationRunNotFoundException extends FintechException {
    public ReconciliationRunNotFoundException(UUID runId) {
        super("Reconciliation run not found: " + runId, "RECONCILIATION_RUN_NOT_FOUND");
    }
}

