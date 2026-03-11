-- V1__create_social_tables.sql

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Posts
CREATE TABLE posts (
    id             UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    author_id      UUID        NOT NULL,
    content        TEXT        NOT NULL CHECK (length(content) > 0 AND length(content) <= 3000),
    visibility     VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    post_type      VARCHAR(20),
    media_url      VARCHAR(500),
    media_type     VARCHAR(20),
    like_count     INTEGER     NOT NULL DEFAULT 0 CHECK (like_count >= 0),
    comment_count  INTEGER     NOT NULL DEFAULT 0 CHECK (comment_count >= 0),
    share_count    INTEGER     NOT NULL DEFAULT 0 CHECK (share_count >= 0),
    view_count     INTEGER     NOT NULL DEFAULT 0 CHECK (view_count >= 0),
    deleted_at     TIMESTAMPTZ,
    flagged        BOOLEAN     NOT NULL DEFAULT FALSE,
    flag_reason    VARCHAR(255),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_posts_author_id  ON posts(author_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_posts_created_at ON posts(created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_posts_visibility ON posts(visibility) WHERE deleted_at IS NULL;

-- Comments
CREATE TABLE comments (
    id                UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    post_id           UUID        NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id         UUID        NOT NULL,
    content           TEXT        NOT NULL CHECK (length(content) > 0 AND length(content) <= 1000),
    parent_comment_id UUID        REFERENCES comments(id) ON DELETE CASCADE,
    like_count        INTEGER     NOT NULL DEFAULT 0,
    deleted_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_comments_post_id ON comments(post_id) WHERE deleted_at IS NULL;

-- Connections
CREATE TABLE connections (
    id            UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    requester_id  UUID        NOT NULL,
    addressee_id  UUID        NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    message       VARCHAR(500),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    responded_at  TIMESTAMPTZ,
    CONSTRAINT uq_connection UNIQUE (requester_id, addressee_id),
    CONSTRAINT chk_no_self_connect CHECK (requester_id <> addressee_id)
);

CREATE INDEX idx_connections_requester ON connections(requester_id);
CREATE INDEX idx_connections_addressee ON connections(addressee_id);
CREATE INDEX idx_connections_status    ON connections(status);

-- Job postings
CREATE TABLE job_postings (
    id                 UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    poster_id          UUID        NOT NULL,
    title              VARCHAR(200) NOT NULL,
    organisation       VARCHAR(100) NOT NULL,
    location           VARCHAR(100) NOT NULL,
    remote             BOOLEAN     NOT NULL DEFAULT FALSE,
    description        TEXT        NOT NULL,
    requirements       TEXT,
    benefits           TEXT,
    job_type           VARCHAR(30) NOT NULL,
    experience_level   VARCHAR(20) NOT NULL,
    salary_min         INTEGER,
    salary_max         INTEGER,
    currency           VARCHAR(3)  NOT NULL DEFAULT 'GBP',
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    expires_at         TIMESTAMPTZ,
    application_count  INTEGER     NOT NULL DEFAULT 0,
    view_count         INTEGER     NOT NULL DEFAULT 0,
    tags               JSONB,
    game_titles        JSONB,
    application_url    VARCHAR(500),
    contact_email      VARCHAR(255),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_salary CHECK (salary_min IS NULL OR salary_max IS NULL OR salary_min <= salary_max)
);

CREATE INDEX idx_jobs_poster    ON job_postings(poster_id);
CREATE INDEX idx_jobs_status    ON job_postings(status);
CREATE INDEX idx_jobs_job_type  ON job_postings(job_type);
CREATE INDEX idx_jobs_created   ON job_postings(created_at DESC);
CREATE INDEX idx_jobs_tags      ON job_postings USING gin(tags);
CREATE INDEX idx_jobs_search    ON job_postings USING gin(to_tsvector('english', title || ' ' || description));

-- Job applications
CREATE TABLE job_applications (
    id               UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id           UUID        NOT NULL REFERENCES job_postings(id) ON DELETE CASCADE,
    applicant_id     UUID        NOT NULL,
    status           VARCHAR(30) NOT NULL DEFAULT 'APPLIED',
    cover_letter     TEXT,
    cv_url           VARCHAR(500),
    portfolio_url    VARCHAR(500),
    recruiter_notes  TEXT,       -- Never returned to applicant in API responses
    applied_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at      TIMESTAMPTZ,
    CONSTRAINT uq_application UNIQUE (job_id, applicant_id)
);

CREATE INDEX idx_applications_job       ON job_applications(job_id);
CREATE INDEX idx_applications_applicant ON job_applications(applicant_id);
CREATE INDEX idx_applications_status    ON job_applications(status);

-- Full-text search on job postings
CREATE OR REPLACE FUNCTION increment_job_views(p_job_id UUID)
RETURNS VOID AS $$
BEGIN
    UPDATE job_postings SET view_count = view_count + 1 WHERE id = p_job_id;
END;
$$ LANGUAGE plpgsql;
