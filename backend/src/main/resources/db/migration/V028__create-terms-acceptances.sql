-- Each version of the terms of service an operator accepted, and when. Kept as evidence: a new version adds a row.
-- Platform-wide, like the users table
CREATE TABLE terms_acceptances (
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    version     TEXT        NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, version)
);
