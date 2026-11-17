-- Ready-made integrations (Shopify, Zendesk) a workspace connects with its own credentials. Once connected, their
-- actions are offered to the assistant next to the workspace's own webhooks.

CREATE TABLE action_integrations (
    workspace_id UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    provider     TEXT        NOT NULL,
    -- The Shopify store (mitienda.myshopify.com) or the Zendesk subdomain; the API's address is built from it
    account      TEXT        NOT NULL,
    -- The Zendesk agent the API token belongs to; null for Shopify
    username     TEXT,
    -- Encrypted at rest; never sent back to the panel
    token        TEXT        NOT NULL,
    enabled      BOOLEAN     NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (workspace_id, provider),
    CONSTRAINT action_integrations_provider_check CHECK (provider IN ('SHOPIFY', 'ZENDESK'))
);

ALTER TABLE action_integrations ENABLE ROW LEVEL SECURITY;
ALTER TABLE action_integrations FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON action_integrations
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());
