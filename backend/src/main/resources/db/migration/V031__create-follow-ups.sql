-- Messages the team schedules to write to a customer later ("¿Te llegó tu pedido?"), from reusable templates. On
-- WhatsApp, after 24 hours without a message of the customer, only a template approved in WhatsApp Manager arrives.
CREATE TABLE follow_up_templates (
    id                      UUID        PRIMARY KEY,
    workspace_id            UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    name                    TEXT        NOT NULL,
    -- What the customer reads; {nombre} becomes their name
    body                    TEXT        NOT NULL,
    -- The approved WhatsApp template sent instead after 24 hours of silence, and its language (es_MX)
    whatsapp_template       TEXT,
    whatsapp_language       TEXT,
    -- Whether that template's {{1}} is the customer's name
    whatsapp_name_parameter BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT follow_up_templates_name_key UNIQUE (workspace_id, name),
    CONSTRAINT follow_up_templates_whatsapp_check CHECK ((whatsapp_template IS NULL) = (whatsapp_language IS NULL))
);

CREATE TABLE follow_ups (
    id                      UUID        PRIMARY KEY,
    workspace_id            UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    conversation_id         UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    template_id             UUID        REFERENCES follow_up_templates (id) ON DELETE SET NULL,
    -- Copied from the template when scheduled, so editing the template later does not change what was promised
    body                    TEXT        NOT NULL,
    whatsapp_template       TEXT,
    whatsapp_language       TEXT,
    whatsapp_name_parameter BOOLEAN     NOT NULL DEFAULT FALSE,
    due_at                  TIMESTAMPTZ NOT NULL,
    status                  TEXT        NOT NULL CHECK (status IN ('SCHEDULED', 'SENT', 'FAILED', 'CANCELLED')),
    failure                 TEXT,
    created_by              UUID        NOT NULL,
    created_by_name         TEXT        NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    -- When it was sent, failed or was cancelled
    finished_at             TIMESTAMPTZ,
    -- The conversation and message it went out as
    sent_conversation_id    UUID,
    message_id              UUID
);

CREATE INDEX follow_ups_due_idx ON follow_ups (workspace_id, due_at) WHERE status = 'SCHEDULED';
CREATE INDEX follow_ups_conversation_idx ON follow_ups (conversation_id, due_at);
CREATE INDEX follow_ups_status_idx ON follow_ups (workspace_id, status, due_at DESC);

ALTER TABLE follow_up_templates ENABLE ROW LEVEL SECURITY;
ALTER TABLE follow_up_templates FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON follow_up_templates
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());

ALTER TABLE follow_ups ENABLE ROW LEVEL SECURITY;
ALTER TABLE follow_ups FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON follow_ups
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());

-- The job finds the workspaces with follow-ups due before any workspace is bound, like the mail job. No row-level
-- security on purpose, and nothing secret in it: a row lives while its follow-up waits
CREATE TABLE follow_up_queue (
    follow_up_id UUID        PRIMARY KEY REFERENCES follow_ups (id) ON DELETE CASCADE,
    workspace_id UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    due_at       TIMESTAMPTZ NOT NULL
);

CREATE INDEX follow_up_queue_due_idx ON follow_up_queue (due_at);
