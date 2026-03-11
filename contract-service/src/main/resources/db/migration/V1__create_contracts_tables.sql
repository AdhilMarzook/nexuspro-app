-- V1__create_contracts_tables.sql

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE contracts (
    id               UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id          UUID        NOT NULL,
    file_name        VARCHAR(255) NOT NULL,
    file_type        VARCHAR(10) NOT NULL,
    extracted_text   TEXT,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    analysis         JSONB,
    risk_score       INTEGER     CHECK (risk_score BETWEEN 0 AND 100),
    risk_level       VARCHAR(20),
    total_clauses    INTEGER,
    flagged_clauses  INTEGER,
    ai_input_tokens  INTEGER,
    ai_output_tokens INTEGER,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    analysed_at      TIMESTAMPTZ,
    text_deleted_at  TIMESTAMPTZ
);

CREATE INDEX idx_contracts_user_id    ON contracts(user_id);
CREATE INDEX idx_contracts_status     ON contracts(status);
CREATE INDEX idx_contracts_created_at ON contracts(created_at DESC);
