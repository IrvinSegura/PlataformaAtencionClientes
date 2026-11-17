-- Questions the assistant could not answer, grouped by their normalized wording, so operators see what to document
-- first. Filled from the QuestionUnanswered event.

CREATE TABLE knowledge_gaps (
    id                UUID        PRIMARY KEY,
    workspace_id      UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    -- Lowercase, without accents, punctuation or repeated spaces: "¿Cuánto cuesta?" and "cuanto cuesta" are one gap
    question_key      TEXT        NOT NULL,
    -- The latest wording visitors used
    question          TEXT        NOT NULL,
    status            TEXT        NOT NULL,
    status_changed_at TIMESTAMPTZ NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT knowledge_gaps_question_key UNIQUE (workspace_id, question_key),
    CONSTRAINT knowledge_gaps_status_check CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED'))
);

-- One row per unanswered question; keyed by the answer message, so an event delivered twice is counted once
CREATE TABLE knowledge_gap_occurrences (
    message_id      UUID        PRIMARY KEY,
    workspace_id    UUID        NOT NULL,
    gap_id          UUID        NOT NULL REFERENCES knowledge_gaps (id) ON DELETE CASCADE,
    conversation_id UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    asked_at        TIMESTAMPTZ NOT NULL
);

CREATE INDEX knowledge_gaps_status_idx ON knowledge_gaps (workspace_id, status);
CREATE INDEX knowledge_gap_occurrences_gap_idx ON knowledge_gap_occurrences (gap_id, asked_at DESC);

ALTER TABLE knowledge_gaps ENABLE ROW LEVEL SECURITY;
ALTER TABLE knowledge_gaps FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON knowledge_gaps
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());

ALTER TABLE knowledge_gap_occurrences ENABLE ROW LEVEL SECURITY;
ALTER TABLE knowledge_gap_occurrences FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON knowledge_gap_occurrences
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());
