-- Plans paid through the payment provider (Stripe) and the usage reported to it.

-- The last state the provider reported of each workspace's subscription. No row: the free plan
CREATE TABLE subscriptions (
    workspace_id         UUID        PRIMARY KEY REFERENCES workspaces (id) ON DELETE CASCADE,
    -- The plan paid for; it applies only while payments are current
    plan_code            TEXT,
    status               TEXT        NOT NULL,
    customer_id          TEXT,
    subscription_id      TEXT,
    current_period_end   TIMESTAMPTZ,
    cancel_at_period_end BOOLEAN     NOT NULL DEFAULT FALSE,
    -- When a payment first failed; the plan keeps working for a grace period after it
    past_due_since       TIMESTAMPTZ,
    -- Creation time of the newest notification applied, so an older one arriving late is ignored
    last_event_at        TIMESTAMPTZ,
    updated_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT subscriptions_status_check CHECK (status IN ('NONE', 'ACTIVE', 'TRIALING', 'PAST_DUE', 'UNPAID',
                                                            'CANCELED', 'INCOMPLETE', 'INCOMPLETE_EXPIRED', 'PAUSED'))
);

ALTER TABLE subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscriptions FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON subscriptions
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());

-- Resolves the provider's customer in a notification to its workspace before the workspace is known, and lists the
-- workspaces whose usage is reported. No row-level security on purpose, and nothing secret in it
CREATE TABLE billing_customers (
    workspace_id UUID PRIMARY KEY REFERENCES workspaces (id) ON DELETE CASCADE,
    customer_id  TEXT NOT NULL UNIQUE
);

-- The provider delivers a notification again until it gets a quick success; an id seen before is ignored.
-- Platform-wide, without row-level security, like the table above
CREATE TABLE payment_events (
    event_id    TEXT        PRIMARY KEY,
    received_at TIMESTAMPTZ NOT NULL
);

-- How much of each month's usage was reported to the provider's meters, so each run reports only what is new
CREATE TABLE usage_reports (
    workspace_id UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    month        DATE        NOT NULL,
    metric       TEXT        NOT NULL,
    quantity     BIGINT      NOT NULL,
    reported_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (workspace_id, month, metric),
    CONSTRAINT usage_reports_quantity_check CHECK (quantity >= 0)
);

ALTER TABLE usage_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE usage_reports FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON usage_reports
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());
