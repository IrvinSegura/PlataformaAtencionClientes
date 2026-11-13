-- Customers who need a person: those who ask for one and those who seem upset. The owner can be told by WhatsApp.

-- When a message of the customer first read as upset; shown to operators and never cleared
ALTER TABLE conversations ADD COLUMN frustrated_at TIMESTAMPTZ;

CREATE TABLE attention_settings (
    workspace_id            UUID        PRIMARY KEY REFERENCES workspaces (id) ON DELETE CASCADE,
    -- An upset customer stops talking to the assistant and waits for the team
    hand_off_frustrated     BOOLEAN     NOT NULL,
    alerts_enabled          BOOLEAN     NOT NULL,
    -- WhatsApp numbers to alert, digits only with the country code
    alert_phones            TEXT[]      NOT NULL,
    alert_on_request        BOOLEAN     NOT NULL,
    alert_on_frustration    BOOLEAN     NOT NULL,
    -- An approved WhatsApp template, needed to reach a number that has not written in the last 24 hours
    alert_template          TEXT,
    alert_template_language TEXT,
    updated_at              TIMESTAMPTZ NOT NULL
);

-- One alert per conversation and reason, so an upset customer who writes ten times alerts once
CREATE TABLE attention_alerts (
    id              UUID        PRIMARY KEY,
    workspace_id    UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    conversation_id UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    reason          TEXT        NOT NULL CHECK (reason IN ('ASKED_FOR_PERSON', 'FRUSTRATED')),
    -- What the customer wrote, cut short
    excerpt         TEXT,
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT attention_alerts_once UNIQUE (conversation_id, reason)
);

CREATE INDEX attention_alerts_recent ON attention_alerts (workspace_id, created_at DESC);

CREATE TABLE attention_alert_deliveries (
    alert_id     UUID        NOT NULL REFERENCES attention_alerts (id) ON DELETE CASCADE,
    workspace_id UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    recipient    TEXT        NOT NULL,
    status       TEXT        NOT NULL CHECK (status IN ('SENT', 'REJECTED', 'FAILED', 'NO_CHANNEL')),
    detail       TEXT,
    delivered_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (alert_id, recipient)
);

ALTER TABLE attention_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE attention_settings FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON attention_settings
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());

ALTER TABLE attention_alerts ENABLE ROW LEVEL SECURITY;
ALTER TABLE attention_alerts FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON attention_alerts
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());

ALTER TABLE attention_alert_deliveries ENABLE ROW LEVEL SECURITY;
ALTER TABLE attention_alert_deliveries FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON attention_alert_deliveries
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());
