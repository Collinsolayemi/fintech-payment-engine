-- V7__add_fx_and_cards.sql
-- Multi-Currency FX Engine and Virtual Card Management

-- 1. Seed Multi-Currency System Chart of Accounts (EUR, GBP, CAD)
INSERT INTO accounts (id, user_id, account_number, account_name, type, currency, status)
VALUES 
    ('00000000-0000-0000-0000-000000001003', NULL, 'SYS-1001-EUR', 'CASH_BANK_MAIN_EUR', 'ASSET', 'EUR', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000001004', NULL, 'SYS-1001-GBP', 'CASH_BANK_MAIN_GBP', 'ASSET', 'GBP', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000001005', NULL, 'SYS-1001-CAD', 'CASH_BANK_MAIN_CAD', 'ASSET', 'CAD', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000003001', NULL, 'SYS-FX-USD', 'FX_TREASURY_CLEARING_USD', 'ASSET', 'USD', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000003002', NULL, 'SYS-FX-EUR', 'FX_TREASURY_CLEARING_EUR', 'ASSET', 'EUR', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000003003', NULL, 'SYS-FX-GBP', 'FX_TREASURY_CLEARING_GBP', 'ASSET', 'GBP', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000003004', NULL, 'SYS-FX-CAD', 'FX_TREASURY_CLEARING_CAD', 'ASSET', 'CAD', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000004002', NULL, 'SYS-4002-USD', 'REVENUE_FX_SPREAD', 'REVENUE', 'USD', 'ACTIVE')
ON CONFLICT (account_number) DO NOTHING;

-- 2. FX Quotations Table
CREATE TABLE fx_quotes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    source_currency VARCHAR(3) NOT NULL,
    target_currency VARCHAR(3) NOT NULL,
    source_amount NUMERIC(19, 4) NOT NULL,
    target_amount NUMERIC(19, 4) NOT NULL,
    exchange_rate NUMERIC(19, 6) NOT NULL,
    fee NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'EXECUTED', 'EXPIRED')),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fx_quotes_user_id ON fx_quotes(user_id);
CREATE INDEX idx_fx_quotes_status ON fx_quotes(status);
CREATE INDEX idx_fx_quotes_expires_at ON fx_quotes(expires_at);

-- 3. Virtual Cards Table
CREATE TABLE virtual_cards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    cardholder_name VARCHAR(128) NOT NULL,
    card_number VARCHAR(16) NOT NULL UNIQUE,
    last_four VARCHAR(4) NOT NULL,
    expiry_month INT NOT NULL CHECK (expiry_month BETWEEN 1 AND 12),
    expiry_year INT NOT NULL CHECK (expiry_year >= 2024),
    cvv VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'FROZEN', 'CANCELLED')),
    card_brand VARCHAR(32) NOT NULL DEFAULT 'VISA' CHECK (card_brand IN ('VISA', 'MASTERCARD')),
    card_type VARCHAR(32) NOT NULL DEFAULT 'VIRTUAL_DEBIT',
    monthly_limit NUMERIC(19, 4) NOT NULL DEFAULT 5000.00,
    online_payments_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    international_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_virtual_cards_user_id ON virtual_cards(user_id);
CREATE INDEX idx_virtual_cards_account_id ON virtual_cards(account_id);
CREATE INDEX idx_virtual_cards_status ON virtual_cards(status);

