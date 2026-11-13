-- V9__add_virtual_accounts.sql
-- Dedicated Virtual Inbound Banking Accounts

CREATE TABLE virtual_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    currency VARCHAR(3) NOT NULL,
    account_number VARCHAR(34) NOT NULL UNIQUE,
    account_name VARCHAR(255) NOT NULL,
    bank_name VARCHAR(100) NOT NULL,
    routing_number VARCHAR(50),
    sort_code VARCHAR(20),
    iban VARCHAR(34),
    bic_swift VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    provider VARCHAR(50) NOT NULL DEFAULT 'SIMULATED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_virtual_account_user_currency UNIQUE (user_id, currency)
);

CREATE INDEX idx_virtual_accounts_user_id ON virtual_accounts(user_id);
CREATE INDEX idx_virtual_accounts_account_id ON virtual_accounts(account_id);
CREATE INDEX idx_virtual_accounts_number ON virtual_accounts(account_number);
CREATE INDEX idx_virtual_accounts_status ON virtual_accounts(status);

-- Ensure NGN platform settlement clearing account exists for inbound virtual account deposits
INSERT INTO accounts (id, user_id, account_number, account_name, type, currency, status)
VALUES ('00000000-0000-0000-0000-000000001006', NULL, 'SYS-1001-NGN', 'CASH_BANK_MAIN_NGN', 'ASSET', 'NGN', 'ACTIVE')
ON CONFLICT (account_number) DO NOTHING;
