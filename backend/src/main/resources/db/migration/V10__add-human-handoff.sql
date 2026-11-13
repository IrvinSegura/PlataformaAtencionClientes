-- Live human handoff: a visitor can ask for a person, and a member of the team takes the conversation over.

ALTER TABLE conversations
    -- Who answers: the assistant, nobody yet (waiting for a person) or an agent of the team
    ADD COLUMN handling             TEXT        NOT NULL DEFAULT 'ASSISTANT',
    ADD COLUMN agent_id             UUID,
    -- Copied when the agent takes over, so the visitor sees a name without reading the users table
    ADD COLUMN agent_name           TEXT,
    ADD COLUMN handoff_requested_at TIMESTAMPTZ,
    ADD CONSTRAINT conversations_handling_check CHECK (handling IN ('ASSISTANT', 'WAITING', 'AGENT')),
    ADD CONSTRAINT conversations_agent_check CHECK ((handling = 'AGENT') = (agent_id IS NOT NULL));

-- The queue of visitors waiting for a person, oldest request first
CREATE INDEX conversations_waiting_idx ON conversations (workspace_id, handoff_requested_at)
    WHERE handling = 'WAITING';

ALTER TABLE conversation_messages
    -- Agent replies only: who wrote them, and their name at the time
    ADD COLUMN author_id   UUID,
    ADD COLUMN author_name TEXT,
    DROP CONSTRAINT conversation_messages_author_check,
    -- SYSTEM messages record what happened (an agent joined, the conversation was closed); their content is a code
    ADD CONSTRAINT conversation_messages_author_check
        CHECK (author IN ('VISITOR', 'ASSISTANT', 'AGENT', 'SYSTEM'));
