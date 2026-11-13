-- Workspaces are the tenants. This table has no row-level security on purpose: it is read before a workspace is
-- known (widget key lookups, sign-in), and the application always filters it by the id it was given.
CREATE TABLE workspaces (
    id              UUID        PRIMARY KEY,
    name            TEXT        NOT NULL,
    widget_key      TEXT        NOT NULL,
    allowed_origins TEXT[]      NOT NULL DEFAULT '{}',
    bot_name        TEXT        NOT NULL,
    welcome_message TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT workspaces_widget_key_key UNIQUE (widget_key)
);

CREATE TABLE workspace_members (
    workspace_id UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role         TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (workspace_id, user_id),
    CONSTRAINT workspace_members_role_check CHECK (role IN ('OWNER', 'ADMIN', 'AGENT'))
);

CREATE INDEX workspace_members_user_idx ON workspace_members (user_id);
