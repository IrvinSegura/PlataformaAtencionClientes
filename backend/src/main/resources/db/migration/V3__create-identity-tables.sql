-- Operators who sign in to the panel. Emails are stored lowercased, so the unique constraint is case-insensitive.
CREATE TABLE users (
    id                    UUID        PRIMARY KEY,
    email                 TEXT        NOT NULL,
    display_name          TEXT        NOT NULL,
    password_hash         TEXT        NOT NULL,
    failed_login_attempts INT         NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT users_email_key UNIQUE (email),
    CONSTRAINT users_email_lowercase_check CHECK (email = lower(email))
);

-- Refresh tokens are stored as SHA-256 hashes, never in clear. Each rotation adds a row to the login's family;
-- presenting a token that was already used revokes the whole family (stolen token reuse detection).
CREATE TABLE refresh_tokens (
    id           UUID        PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    workspace_id UUID        NOT NULL,
    family_id    UUID        NOT NULL,
    token_hash   BYTEA       NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    used_at      TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT refresh_tokens_token_hash_key UNIQUE (token_hash)
);

CREATE INDEX refresh_tokens_family_idx ON refresh_tokens (family_id);
CREATE INDEX refresh_tokens_expires_at_idx ON refresh_tokens (expires_at);
