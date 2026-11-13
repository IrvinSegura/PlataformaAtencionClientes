-- Conversations can come from WhatsApp besides the web widget, and each answer records which kind of model answered.

ALTER TABLE conversations
    ADD COLUMN channel      TEXT NOT NULL DEFAULT 'WEB',
    -- WhatsApp: the customer's number as WhatsApp reports it, which the channel has verified
    ADD COLUMN contact_id   TEXT,
    ADD COLUMN contact_name TEXT,
    -- Only web visitors hold a conversation token; WhatsApp customers are identified by their number
    ALTER COLUMN visitor_token_hash DROP NOT NULL,
    ADD CONSTRAINT conversations_channel_check CHECK (channel IN ('WEB', 'WHATSAPP')),
    ADD CONSTRAINT conversations_channel_identity_check
        CHECK ((channel = 'WEB') = (visitor_token_hash IS NOT NULL) AND (channel = 'WEB') = (contact_id IS NULL));

-- One open conversation per contact and channel: two messages arriving at once cannot open two conversations
CREATE UNIQUE INDEX conversations_open_contact_idx ON conversations (workspace_id, channel, contact_id)
    WHERE status = 'OPEN' AND contact_id IS NOT NULL;

ALTER TABLE conversation_messages
    -- Assistant answers: the kind of model chosen for the question, and why
    ADD COLUMN model_tier     TEXT,
    ADD COLUMN routing_reason TEXT,
    -- Assistant answers that proposed an action the visitor has to confirm
    ADD COLUMN action_run_id  UUID,
    ADD CONSTRAINT conversation_messages_model_tier_check CHECK (model_tier IN ('SIMPLE', 'COMPLEX'));
