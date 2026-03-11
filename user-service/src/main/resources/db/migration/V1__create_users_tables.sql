-- V1__create_users_tables.sql
-- NexusPro User Service - Initial Schema

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Users table
CREATE TABLE users (
    id                          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email                       VARCHAR(255) NOT NULL UNIQUE,
    username                    VARCHAR(50)  NOT NULL UNIQUE,
    password_hash               VARCHAR(255) NOT NULL,
    full_name                   VARCHAR(100) NOT NULL,
    role                        VARCHAR(20)  NOT NULL DEFAULT 'PLAYER',
    email_verified              BOOLEAN      NOT NULL DEFAULT FALSE,
    email_verification_token    VARCHAR(64),
    email_verification_expiry   TIMESTAMPTZ,
    mfa_enabled                 BOOLEAN      NOT NULL DEFAULT FALSE,
    mfa_secret                  VARCHAR(64),
    failed_login_attempts       INTEGER      NOT NULL DEFAULT 0,
    lockout_until               TIMESTAMPTZ,
    password_reset_token        VARCHAR(64),
    password_reset_expiry       TIMESTAMPTZ,
    oauth_provider              VARCHAR(50),
    oauth_provider_id           VARCHAR(255),
    plan                        VARCHAR(20)  NOT NULL DEFAULT 'FREE',
    plan_expires_at             TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_login_at               TIMESTAMPTZ,
    last_login_ip               VARCHAR(45),
    deleted_at                  TIMESTAMPTZ  -- Soft delete for GDPR
);

-- Indexes
CREATE INDEX idx_users_email_active   ON users(email) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_username_active ON users(username) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_oauth          ON users(oauth_provider, oauth_provider_id);

-- Refresh tokens
CREATE TABLE refresh_tokens (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash    VARCHAR(64)  NOT NULL UNIQUE,  -- SHA-256 hash
    expires_at    TIMESTAMPTZ  NOT NULL,
    used          BOOLEAN      NOT NULL DEFAULT FALSE,
    issued_to_ip  VARCHAR(45),
    user_agent    VARCHAR(255),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expiry  ON refresh_tokens(expires_at);

-- Audit log for security events
CREATE TABLE security_audit_log (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID,
    event_type  VARCHAR(50)  NOT NULL,
    ip_address  VARCHAR(45),
    user_agent  VARCHAR(255),
    details     JSONB,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_user_id    ON security_audit_log(user_id);
CREATE INDEX idx_audit_event_type ON security_audit_log(event_type);
CREATE INDEX idx_audit_created_at ON security_audit_log(created_at);

-- Auto-update updated_at
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
