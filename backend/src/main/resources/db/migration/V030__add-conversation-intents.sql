-- What each conversation is about (shipping, returns, payments...), tagged from the customer's words without calling the
-- model, so the team can filter conversations by topic and correct the tags by hand.
ALTER TABLE conversations
    ADD COLUMN intents        TEXT[]  NOT NULL DEFAULT '{}',
    -- Set once the team chose the topics by hand: later messages no longer add any
    ADD COLUMN intents_edited BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT conversations_intents_check CHECK (intents <@ ARRAY['ORDER_STATUS', 'SHIPPING', 'RETURNS',
        'PAYMENTS', 'PRICING', 'AVAILABILITY', 'HOURS_AND_LOCATION', 'ACCOUNT', 'COMPLAINT']);

CREATE INDEX conversations_intents_idx ON conversations USING gin (intents);
