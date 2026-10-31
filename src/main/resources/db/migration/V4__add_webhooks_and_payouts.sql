-- V4__add_webhooks_and_payouts.sql
-- Inbound Deposit Webhooks and Outbound Payouts with Two-Phase Escrow

CREATE TABLE webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(64) NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('RECEIVED', 'PROCESSED', 'FAILED', 'DUPLICATE', 'IGNORED')),
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_webhook_provider_event_id UNIQUE (provider, event_id)
);

CREATE INDEX idx_webhook_events_provider_event_id ON webhook_events(provider, event_id);
CREATE INDEX idx_webhook_events_status ON webhook_events(status);
CREATE INDEX idx_webhook_events_created_at ON webhook_events(created_at);

CREATE TABLE payouts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference VARCHAR(64) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    fee NUMERIC(19, 4) NOT NULL DEFAULT 0.0000 CHECK (fee >= 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING_ESCROW', 'PROCESSING', 'COMPLETED', 'FAILED', 'REVERSED')),
    destination_bank_code VARCHAR(32) NOT NULL,
    destination_account_number VARCHAR(64) NOT NULL,
    destination_account_name VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) UNIQUE,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payouts_reference ON payouts(reference);
CREATE INDEX idx_payouts_user_id ON payouts(user_id);
CREATE INDEX idx_payouts_status ON payouts(status);
CREATE INDEX idx_payouts_idempotency_key ON payouts(idempotency_key);

