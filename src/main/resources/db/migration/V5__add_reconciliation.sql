-- V5__add_reconciliation.sql
-- Automated Reconciliation Engine: run history and detected discrepancies

CREATE TABLE reconciliation_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_type VARCHAR(32) NOT NULL CHECK (run_type IN ('INTERNAL_INTEGRITY', 'GATEWAY_SETTLEMENT')),
    status VARCHAR(32) NOT NULL CHECK (status IN ('RUNNING', 'PASSED', 'FAILED', 'ERROR')),
    provider VARCHAR(64),
    triggered_by VARCHAR(255) NOT NULL,
    period_start TIMESTAMPTZ,
    period_end TIMESTAMPTZ,
    total_debits NUMERIC(19, 4),
    total_credits NUMERIC(19, 4),
    external_total NUMERIC(19, 4),
    ledger_total NUMERIC(19, 4),
    records_checked INT,
    discrepancy_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_reconciliation_runs_started_at ON reconciliation_runs(started_at);
CREATE INDEX idx_reconciliation_runs_status ON reconciliation_runs(status);
CREATE INDEX idx_reconciliation_runs_type ON reconciliation_runs(run_type);

CREATE TABLE reconciliation_discrepancies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL REFERENCES reconciliation_runs(id) ON DELETE RESTRICT,
    discrepancy_type VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM')),
    reference VARCHAR(128),
    account_id UUID,
    currency VARCHAR(3),
    expected_amount NUMERIC(19, 4),
    actual_amount NUMERIC(19, 4),
    details TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reconciliation_discrepancies_run_id ON reconciliation_discrepancies(run_id);
CREATE INDEX idx_reconciliation_discrepancies_type ON reconciliation_discrepancies(discrepancy_type);

