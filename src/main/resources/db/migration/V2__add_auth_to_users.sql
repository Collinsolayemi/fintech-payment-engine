-- V2__add_auth_to_users.sql
-- Adds authentication credentials and authorization roles to the users table

ALTER TABLE users ADD COLUMN password_hash VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN role VARCHAR(32) NOT NULL DEFAULT 'ROLE_USER';

