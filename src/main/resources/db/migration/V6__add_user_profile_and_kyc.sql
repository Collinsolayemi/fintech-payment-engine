-- V6__add_user_profile_and_kyc.sql
-- User Profile Enhancements and Tier Upgrade Applications

-- 1. Add profile and KYC status attributes to users table
ALTER TABLE users ADD COLUMN phone_number VARCHAR(32);
ALTER TABLE users ADD COLUMN address_line VARCHAR(255);
ALTER TABLE users ADD COLUMN city VARCHAR(100);
ALTER TABLE users ADD COLUMN country VARCHAR(64);
ALTER TABLE users ADD COLUMN postal_code VARCHAR(20);
ALTER TABLE users ADD COLUMN date_of_birth DATE;
ALTER TABLE users ADD COLUMN kyc_status VARCHAR(32) NOT NULL DEFAULT 'APPROVED';

-- 2. KYC Tier Upgrade Applications Table
CREATE TABLE kyc_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    target_tier INT NOT NULL CHECK (target_tier IN (2, 3)),
    id_type VARCHAR(64) NOT NULL,
    id_number VARCHAR(128) NOT NULL,
    document_url VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED' CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED')),
    rejection_reason TEXT,
    reviewed_by VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_kyc_applications_user_id ON kyc_applications(user_id);
CREATE INDEX idx_kyc_applications_status ON kyc_applications(status);
CREATE INDEX idx_kyc_applications_created_at ON kyc_applications(created_at);

