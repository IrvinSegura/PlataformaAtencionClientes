-- Recharges paid once through the payment provider: extra units of a limited metric for the month they were paid in
CREATE TABLE usage_top_ups (
    -- The provider's checkout session, so a payment confirmed twice adds once
    reference    TEXT        PRIMARY KEY,
    workspace_id UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    -- First day of the month
    month        DATE        NOT NULL,
    metric       TEXT        NOT NULL CHECK (metric IN ('ANSWERS', 'ACTION_RUNS', 'WHATSAPP_MESSAGES')),
    quantity     BIGINT      NOT NULL CHECK (quantity > 0),
    -- The recharge bought, as named in the configuration then
    top_up       TEXT        NOT NULL,
    paid_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX usage_top_ups_month ON usage_top_ups (workspace_id, month);

ALTER TABLE usage_top_ups ENABLE ROW LEVEL SECURITY;
ALTER TABLE usage_top_ups FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON usage_top_ups
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());
