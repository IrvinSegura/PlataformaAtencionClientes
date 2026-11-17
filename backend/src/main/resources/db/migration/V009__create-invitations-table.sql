-- Invitations to join a workspace. The link holds a secret token; only its SHA-256 hash is stored, like refresh
-- tokens. No row-level security, like workspaces: an invitation is looked up by its token before anyone knows which
-- workspace it belongs to, and every other query filters by the workspace explicitly.
CREATE TABLE invitations (
    id           UUID        PRIMARY KEY,
    workspace_id UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    email        TEXT        NOT NULL,
    role         TEXT        NOT NULL,
    token_hash   BYTEA       NOT NULL,
    invited_by   UUID        REFERENCES users (id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    accepted_at  TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ,
    CONSTRAINT invitations_token_hash_key UNIQUE (token_hash),
    CONSTRAINT invitations_email_lowercase_check CHECK (email = lower(email)),
    -- Nobody joins as owner: a workspace keeps the one who created it
    CONSTRAINT invitations_role_check CHECK (role IN ('ADMIN', 'AGENT'))
);

CREATE INDEX invitations_workspace_idx ON invitations (workspace_id, created_at DESC);
