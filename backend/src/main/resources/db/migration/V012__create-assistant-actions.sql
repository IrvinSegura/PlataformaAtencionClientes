-- Actions the assistant can take in each business's own systems (look up an order, change an address). Each one is a
-- webhook of the business that the platform calls with a signed request when the language model asks for it.

CREATE TABLE assistant_actions (
    id                    UUID        PRIMARY KEY,
    workspace_id          UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    -- The tool name the language model sees, e.g. order_status
    name                  TEXT        NOT NULL,
    -- What people see, e.g. in the confirmation the visitor is asked for
    title                 TEXT        NOT NULL,
    -- Tells the model when to use it
    description           TEXT        NOT NULL,
    endpoint_url          TEXT        NOT NULL,
    parameters            JSONB       NOT NULL DEFAULT '[]',
    -- Actions that change something only run after the visitor confirms them
    requires_confirmation BOOLEAN     NOT NULL,
    enabled               BOOLEAN     NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT assistant_actions_name_key UNIQUE (workspace_id, name)
);

-- The secret each workspace's webhooks use to check that a request comes from the platform. Encrypted at rest
CREATE TABLE action_settings (
    workspace_id   UUID        PRIMARY KEY REFERENCES workspaces (id) ON DELETE CASCADE,
    signing_secret TEXT        NOT NULL,
    rotated_at     TIMESTAMPTZ NOT NULL
);

-- Every time the assistant used an action, or proposed one the visitor had to confirm
CREATE TABLE action_runs (
    id              UUID        PRIMARY KEY,
    workspace_id    UUID        NOT NULL,
    action_id       UUID        REFERENCES assistant_actions (id) ON DELETE SET NULL,
    -- Copied, so the history still reads well after the action is renamed or deleted
    action_name     TEXT        NOT NULL,
    action_title    TEXT        NOT NULL,
    conversation_id UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    -- The assistant answer that ran or proposed it
    message_id      UUID        NOT NULL,
    arguments       JSONB       NOT NULL,
    status          TEXT        NOT NULL,
    http_status     INT,
    -- The start of the webhook's response, as given to the model
    result          TEXT,
    error           TEXT,
    latency_ms      INT,
    created_at      TIMESTAMPTZ NOT NULL,
    -- Pending confirmations stop being confirmable after this
    expires_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    CONSTRAINT action_runs_status_check CHECK (status IN ('PENDING_CONFIRMATION', 'RUNNING', 'SUCCEEDED', 'FAILED',
                                                          'CANCELLED', 'EXPIRED', 'REJECTED'))
);

CREATE INDEX action_runs_conversation_idx ON action_runs (conversation_id, created_at);

ALTER TABLE assistant_actions ENABLE ROW LEVEL SECURITY;
ALTER TABLE assistant_actions FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON assistant_actions
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());

ALTER TABLE action_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE action_settings FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON action_settings
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());

ALTER TABLE action_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE action_runs FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON action_runs
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());
