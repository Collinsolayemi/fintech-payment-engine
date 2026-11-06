CREATE TABLE beneficiaries (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    type VARCHAR(16) NOT NULL CHECK (type IN ('WALLET', 'BANK')),
    nickname VARCHAR(100) NOT NULL,
    recipient_account_id UUID REFERENCES accounts(id),
    bank_code VARCHAR(32),
    account_number VARCHAR(64),
    account_name VARCHAR(200) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    destination_key VARCHAR(200) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_beneficiary_destination UNIQUE (user_id, destination_key),
    CONSTRAINT ck_beneficiary_destination CHECK (
        (type = 'WALLET' AND recipient_account_id IS NOT NULL AND bank_code IS NULL AND account_number IS NULL)
        OR (type = 'BANK' AND recipient_account_id IS NULL AND bank_code IS NOT NULL AND account_number IS NOT NULL)
    )
);
CREATE INDEX idx_beneficiaries_user_created ON beneficiaries(user_id, created_at DESC);
