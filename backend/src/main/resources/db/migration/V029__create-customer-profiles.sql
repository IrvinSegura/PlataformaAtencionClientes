-- What the team knows about a customer who writes through a channel (WhatsApp number, email address, Messenger or
-- Instagram id), across all their conversations. How many conversations they had and when is read from the
-- conversations themselves; only what people write is stored here.
CREATE TABLE customer_profiles (
    workspace_id    UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    channel         TEXT        NOT NULL,
    contact_id      TEXT        NOT NULL,
    -- The name the team knows the customer by, over the one the channel reports
    display_name    TEXT,
    -- For the team only: never sent to the model or the customer
    notes           TEXT        NOT NULL DEFAULT '',
    -- Off when the customer asked not to be remembered: the assistant stops reading their earlier conversations
    remember        BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at      TIMESTAMPTZ NOT NULL,
    updated_by_name TEXT,
    PRIMARY KEY (workspace_id, channel, contact_id)
);

ALTER TABLE customer_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE customer_profiles FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON customer_profiles
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());

-- A customer's conversations, newest first: their history in the panel and the assistant's memory of them
CREATE INDEX conversations_contact_idx ON conversations (workspace_id, channel, contact_id, last_activity_at DESC)
    WHERE contact_id IS NOT NULL;
