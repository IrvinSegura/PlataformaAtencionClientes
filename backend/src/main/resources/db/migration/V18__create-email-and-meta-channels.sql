-- Customers can also write by email, Facebook Messenger and Instagram.

ALTER TABLE conversations
    DROP CONSTRAINT conversations_channel_check,
    ADD CONSTRAINT conversations_channel_check
        CHECK (channel IN ('WEB', 'WHATSAPP', 'EMAIL', 'MESSENGER', 'INSTAGRAM'));

-- Messages received on the newer channels; one seen before (Meta repeating a delivery, a mailbox read twice) is
-- ignored
CREATE TABLE channel_inbound_messages (
    workspace_id UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    channel      TEXT        NOT NULL,
    message_id   TEXT        NOT NULL,
    received_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (workspace_id, channel, message_id)
);

-- Messenger and Instagram, both through a Facebook Page and Meta's Messenger Platform. Like WhatsApp, the public key
-- in the webhook URL resolves to its workspace before the workspace is known: no row-level security, nothing secret
CREATE TABLE meta_channel_keys (
    channel_key  TEXT PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    platform     TEXT NOT NULL,
    CONSTRAINT meta_channel_keys_workspace_platform_key UNIQUE (workspace_id, platform),
    CONSTRAINT meta_channel_keys_platform_check CHECK (platform IN ('MESSENGER', 'INSTAGRAM'))
);

CREATE TABLE meta_channels (
    workspace_id UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    platform     TEXT        NOT NULL,
    -- The Facebook Page or Instagram professional account that receives the messages
    account_id   TEXT        NOT NULL,
    display_name TEXT,
    -- Encrypted at rest: the Page access token sends messages, the app secret verifies Meta's signatures
    access_token TEXT        NOT NULL,
    app_secret   TEXT        NOT NULL,
    verify_token TEXT        NOT NULL,
    enabled      BOOLEAN     NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (workspace_id, platform),
    CONSTRAINT meta_channels_platform_check CHECK (platform IN ('MESSENGER', 'INSTAGRAM'))
);

-- Email: a mailbox read over IMAP and answered over SMTP

-- The mail job lists the workspaces to read before any workspace is bound, like the billing customers. No row-level
-- security on purpose, and nothing secret in it
CREATE TABLE email_channel_workspaces (
    workspace_id UUID PRIMARY KEY REFERENCES workspaces (id) ON DELETE CASCADE
);

CREATE TABLE email_channels (
    workspace_id UUID PRIMARY KEY REFERENCES workspaces (id) ON DELETE CASCADE,
    address      TEXT        NOT NULL,
    display_name TEXT,
    imap_host    TEXT        NOT NULL,
    imap_port    INTEGER     NOT NULL,
    smtp_host    TEXT        NOT NULL,
    smtp_port    INTEGER     NOT NULL,
    username     TEXT        NOT NULL,
    -- Encrypted at rest; for Gmail, an app password
    password     TEXT        NOT NULL,
    enabled      BOOLEAN     NOT NULL,
    -- Where reading stopped: IMAP identifies messages by UID, valid while the mailbox keeps its UIDVALIDITY
    uid_validity BIGINT,
    last_uid     BIGINT,
    checked_at   TIMESTAMPTZ,
    -- Why the last attempt to read the mailbox failed, for the panel; null when it worked
    last_error   TEXT,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL
);

-- What replies to a customer need to land in the same thread of their mail client
CREATE TABLE email_threads (
    workspace_id    UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    contact_address TEXT        NOT NULL,
    subject         TEXT,
    last_message_id TEXT,
    reference_ids   TEXT,
    updated_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (workspace_id, contact_address)
);

ALTER TABLE channel_inbound_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE channel_inbound_messages FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON channel_inbound_messages
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());

ALTER TABLE meta_channels ENABLE ROW LEVEL SECURITY;
ALTER TABLE meta_channels FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON meta_channels
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());

ALTER TABLE email_channels ENABLE ROW LEVEL SECURITY;
ALTER TABLE email_channels FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON email_channels
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());

ALTER TABLE email_threads ENABLE ROW LEVEL SECURITY;
ALTER TABLE email_threads FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON email_threads
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());
