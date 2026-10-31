-- V1__init_fintech_schema.sql
-- Core Double-Entry Ledger and Account Management Schema

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. Users Table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    kyc_tier INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);

-- 2. Accounts Table (Covers both System Chart of Accounts & User Wallets)
CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    account_number VARCHAR(64) NOT NULL UNIQUE,
    account_name VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL CHECK (type IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_accounts_user_id ON accounts(user_id);
CREATE INDEX idx_accounts_type ON accounts(type);
CREATE INDEX idx_accounts_status ON accounts(status);

-- 3. Transactions Table (Header record representing the business transaction)
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference VARCHAR(64) NOT NULL UNIQUE,
    idempotency_key VARCHAR(128) UNIQUE,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REVERSED')),
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_transactions_reference ON transactions(reference);
CREATE INDEX idx_transactions_idempotency_key ON transactions(idempotency_key);
CREATE INDEX idx_transactions_created_at ON transactions(created_at);

-- 4. Ledger Entries Table (Strictly Append-Only)
CREATE TABLE ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE RESTRICT,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    entry_type VARCHAR(16) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ledger_entries_account_id ON ledger_entries(account_id);
CREATE INDEX idx_ledger_entries_transaction_id ON ledger_entries(transaction_id);
CREATE INDEX idx_ledger_entries_created_at ON ledger_entries(created_at);

-- 5. Idempotency Records Table
CREATE TABLE idempotency_records (
    key VARCHAR(128) PRIMARY KEY,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('IN_FLIGHT', 'COMPLETED', 'FAILED')),
    response_code INT,
    response_body TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_idempotency_records_expires_at ON idempotency_records(expires_at);

-- 6. Seed Baseline System Accounts (Platform Chart of Accounts)
INSERT INTO accounts (id, user_id, account_number, account_name, type, currency, status)
VALUES 
    ('00000000-0000-0000-0000-000000001001', NULL, 'SYS-1001-USD', 'CASH_BANK_MAIN', 'ASSET', 'USD', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000001002', NULL, 'SYS-1002-USD', 'GATEWAY_RECEIVABLE_STRIPE', 'ASSET', 'USD', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000002002', NULL, 'SYS-2002-USD', 'PENDING_SETTLEMENTS', 'LIABILITY', 'USD', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000004001', NULL, 'SYS-4001-USD', 'REVENUE_TRANSFER_FEES', 'REVENUE', 'USD', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000005001', NULL, 'SYS-5001-USD', 'EXPENSE_GATEWAY_FEES', 'EXPENSE', 'USD', 'ACTIVE');
