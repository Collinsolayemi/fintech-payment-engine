-- V11__add_payment_requests.sql
-- Peer-to-Peer Payment Requests & Shareable Payment Links

CREATE TABLE payment_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_code VARCHAR(32) NOT NULL UNIQUE,
    requester_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    requester_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    payer_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    payer_email VARCHAR(255),
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    description VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PAID', 'DECLINED', 'CANCELLED', 'EXPIRED')),
    transaction_reference VARCHAR(100),
    expires_at TIMESTAMPTZ,
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_requests_requester ON payment_requests(requester_user_id);
CREATE INDEX idx_payment_requests_payer ON payment_requests(payer_user_id);
CREATE INDEX idx_payment_requests_code ON payment_requests(request_code);
CREATE INDEX idx_payment_requests_status ON payment_requests(status);

