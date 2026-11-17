-- What each workspace consumed per day (UTC), the basis for billing it. One row per metric and dimension (the model
-- for tokens, the channel for answers, the action for action runs), incremented in the same transaction as the work
-- it measures, so usage is never counted twice or kept when that work rolls back.
CREATE TABLE usage_daily (
    workspace_id UUID   NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    day          DATE   NOT NULL,
    metric       TEXT   NOT NULL,
    dimension    TEXT   NOT NULL DEFAULT '',
    quantity     BIGINT NOT NULL,
    PRIMARY KEY (workspace_id, day, metric, dimension),
    CONSTRAINT usage_daily_metric_check
        CHECK (metric IN ('ANSWERS', 'INPUT_TOKENS', 'OUTPUT_TOKENS', 'ACTION_RUNS', 'WHATSAPP_MESSAGES')),
    CONSTRAINT usage_daily_quantity_check CHECK (quantity >= 0)
);

ALTER TABLE usage_daily ENABLE ROW LEVEL SECURITY;
ALTER TABLE usage_daily FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON usage_daily
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());
