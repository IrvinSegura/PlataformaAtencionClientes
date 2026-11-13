-- Visitor conversations held through the widget. Protected by row-level security like the knowledge base.

CREATE TABLE conversations (
    id                 UUID        PRIMARY KEY,
    workspace_id       UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    -- SHA-256 of the secret the visitor's browser holds; whoever has the secret can continue the conversation
    visitor_token_hash BYTEA       NOT NULL,
    status             TEXT        NOT NULL,
    question_count     INT         NOT NULL DEFAULT 0,
    origin             TEXT,
    started_at         TIMESTAMPTZ NOT NULL,
    last_activity_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT conversations_status_check CHECK (status IN ('OPEN', 'CLOSED'))
);

CREATE INDEX conversations_workspace_activity_idx ON conversations (workspace_id, last_activity_at DESC);

CREATE TABLE conversation_messages (
    id              UUID        PRIMARY KEY,
    workspace_id    UUID        NOT NULL,
    conversation_id UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    author          TEXT        NOT NULL,
    content         TEXT        NOT NULL,
    -- Assistant messages only: how the question was handled, which sources the answer cites and what it cost
    outcome         TEXT,
    citations       JSONB       NOT NULL DEFAULT '[]',
    model           TEXT,
    input_tokens    INT,
    output_tokens   INT,
    latency_ms      INT,
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT conversation_messages_author_check CHECK (author IN ('VISITOR', 'ASSISTANT')),
    CONSTRAINT conversation_messages_outcome_check CHECK (outcome IN ('ANSWERED', 'NO_ANSWER', 'FAILED'))
);

CREATE INDEX conversation_messages_conversation_idx ON conversation_messages (conversation_id, created_at);
-- Questions the assistant could not answer, for the knowledge gaps report
CREATE INDEX conversation_messages_no_answer_idx ON conversation_messages (workspace_id, created_at DESC)
    WHERE outcome = 'NO_ANSWER';

ALTER TABLE conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE conversations FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON conversations
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());

ALTER TABLE conversation_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE conversation_messages FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON conversation_messages
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());
