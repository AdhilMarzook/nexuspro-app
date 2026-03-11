-- V1__create_wellbeing_tables.sql

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE wellbeing_entries (
    id                  UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id             UUID        NOT NULL,
    entry_date          DATE        NOT NULL,
    mood_score          INTEGER     CHECK (mood_score BETWEEN 0 AND 100),
    sleep_hours         INTEGER     CHECK (sleep_hours BETWEEN 0 AND 240),
    sleep_quality       INTEGER     CHECK (sleep_quality BETWEEN 0 AND 100),
    training_hours      INTEGER     CHECK (training_hours BETWEEN 0 AND 240),
    stress_level        INTEGER     CHECK (stress_level BETWEEN 0 AND 100),
    energy_level        INTEGER     CHECK (energy_level BETWEEN 0 AND 100),
    focus_level         INTEGER     CHECK (focus_level BETWEEN 0 AND 100),
    physical_pain       INTEGER     CHECK (physical_pain BETWEEN 0 AND 100),
    social_interaction  INTEGER     CHECK (social_interaction BETWEEN 0 AND 100),
    work_life_balance   INTEGER     CHECK (work_life_balance BETWEEN 0 AND 100),
    burnout_risk_score  INTEGER     CHECK (burnout_risk_score BETWEEN 0 AND 100),
    wellbeing_index     INTEGER     CHECK (wellbeing_index BETWEEN 0 AND 100),
    journal             TEXT        CHECK (length(journal) <= 5000),
    burnout_level       VARCHAR(20),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_date UNIQUE (user_id, entry_date)
);

CREATE INDEX idx_wellbeing_user_id   ON wellbeing_entries(user_id);
CREATE INDEX idx_wellbeing_user_date ON wellbeing_entries(user_id, entry_date DESC);
CREATE INDEX idx_wellbeing_burnout   ON wellbeing_entries(burnout_level);
