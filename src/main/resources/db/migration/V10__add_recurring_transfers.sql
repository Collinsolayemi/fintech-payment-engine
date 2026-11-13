-- V10__add_recurring_transfers.sql
-- Scheduled & Recurring Transfers (Standing Orders)

CREATE TABLE recurring_transfers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    sender_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    recipient_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    description VARCHAR(255),
    frequency VARCHAR(20) NOT NULL CHECK (frequency IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'PAUSED', 'COMPLETED', 'CANCELLED')),
    next_run_at TIMESTAMPTZ NOT NULL,
    end_date TIMESTAMPTZ,
    last_run_at TIMESTAMPTZ,
    last_transaction_reference VARCHAR(100),
    last_failure_reason VARCHAR(500),
    execution_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recurring_transfers_user_id ON recurring_transfers(user_id);
CREATE INDEX idx_recurring_transfers_due ON recurring_transfers(status, next_run_at);

