-- The share of a plan's monthly allowance each workspace was told it reached, so each one is told once a month
CREATE TABLE usage_alerts (
    workspace_id UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    -- First day of the month
    month        DATE        NOT NULL,
    metric       TEXT        NOT NULL CHECK (metric IN ('ANSWERS', 'ACTION_RUNS', 'WHATSAPP_MESSAGES')),
    percent      INT         NOT NULL CHECK (percent BETWEEN 1 AND 100),
    reached_at   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (workspace_id, month, metric, percent)
);

ALTER TABLE usage_alerts ENABLE ROW LEVEL SECURITY;
ALTER TABLE usage_alerts FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON usage_alerts
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());

-- Tell the owner by WhatsApp when the plan nears its limit; on for workspaces that already alert someone
ALTER TABLE attention_settings ADD COLUMN alert_on_usage BOOLEAN NOT NULL DEFAULT TRUE;
