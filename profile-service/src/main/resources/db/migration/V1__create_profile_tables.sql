-- V1__create_profile_tables.sql

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE career_profiles (
    id                  UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id             UUID        NOT NULL UNIQUE,
    username            VARCHAR(50) NOT NULL UNIQUE,
    display_name        VARCHAR(100),
    bio                 TEXT,
    primary_game        VARCHAR(100),
    primary_role        VARCHAR(50),
    country_code        VARCHAR(10),
    avatar_url          VARCHAR(255),
    banner_url          VARCHAR(255),
    current_team        VARCHAR(50),
    current_tier        VARCHAR(50),
    twitter_url         VARCHAR(255),
    twitch_url          VARCHAR(255),
    youtube_url         VARCHAR(255),
    linkedin_url        VARCHAR(255),
    portfolio_url       VARCHAR(255),
    career_score        INTEGER     NOT NULL DEFAULT 0,
    skills              JSONB,
    passport_verified   BOOLEAN     NOT NULL DEFAULT FALSE,
    verified_at         TIMESTAMPTZ,
    completion_pct      INTEGER     NOT NULL DEFAULT 0,
    plan                VARCHAR(20) NOT NULL DEFAULT 'FREE',
    visibility          VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_profiles_user_id  ON career_profiles(user_id);
CREATE INDEX idx_profiles_username ON career_profiles(username);

CREATE TABLE tournaments (
    id                  UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    profile_id          UUID        NOT NULL REFERENCES career_profiles(id) ON DELETE CASCADE,
    name                VARCHAR(200) NOT NULL,
    game                VARCHAR(50) NOT NULL,
    event_date          DATE        NOT NULL,
    placement           VARCHAR(100) NOT NULL,
    total_teams         INTEGER,
    prize_amount_gbp    INTEGER,
    organiser           VARCHAR(100),
    team_name           VARCHAR(100),
    format              VARCHAR(50),
    verified            BOOLEAN     NOT NULL DEFAULT FALSE,
    verification_source VARCHAR(255),
    verified_at         TIMESTAMPTZ,
    career_points       INTEGER     NOT NULL DEFAULT 0,
    notes               VARCHAR(500),
    proof_url           VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tournaments_profile_id ON tournaments(profile_id);
CREATE INDEX idx_tournaments_event_date ON tournaments(event_date DESC);

CREATE TABLE certifications (
    id               UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    profile_id       UUID        NOT NULL REFERENCES career_profiles(id) ON DELETE CASCADE,
    name             VARCHAR(200) NOT NULL,
    issuer           VARCHAR(100) NOT NULL,
    issue_date       DATE,
    expiry_date      DATE,
    credential_id    VARCHAR(200),
    credential_url   VARCHAR(500),
    verified         BOOLEAN     NOT NULL DEFAULT FALSE,
    verified_at      TIMESTAMPTZ,
    category         VARCHAR(30),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_certifications_profile_id ON certifications(profile_id);
