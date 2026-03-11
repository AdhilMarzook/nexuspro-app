-- V1__create_discovery_tables.sql

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE career_assessments (
    id              UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID        NOT NULL,
    answers         JSONB       NOT NULL,
    results         JSONB,
    top_match       VARCHAR(100),
    match_score     INTEGER,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ
);

CREATE INDEX idx_assessments_user_id ON career_assessments(user_id);
CREATE INDEX idx_assessments_status  ON career_assessments(status);
