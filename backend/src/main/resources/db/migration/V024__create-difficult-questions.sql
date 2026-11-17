-- Questions the assistant struggled with: the fast model could not answer them, or the customer showed the answer did
-- not help. Open ones go straight to the capable model; the team can teach their answer to the knowledge base.
CREATE TABLE difficult_questions (
    id                UUID        PRIMARY KEY,
    workspace_id      UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    -- Lowercase, without accents or punctuation, as for knowledge gaps
    question_key      TEXT        NOT NULL,
    -- The latest wording customers used
    question          TEXT        NOT NULL,
    -- The words that say what it is about, to find the same question asked in other words
    words             TEXT[]      NOT NULL,
    reason            TEXT        NOT NULL CHECK (reason IN ('ESCALATED', 'UNHELPFUL', 'ASKED_AGAIN', 'UNSATISFIED')),
    status            TEXT        NOT NULL CHECK (status IN ('OPEN', 'TAUGHT', 'DISMISSED')),
    -- What the assistant answered the last time
    last_answer       TEXT,
    conversation_id   UUID        REFERENCES conversations (id) ON DELETE SET NULL,
    occurrences       INT         NOT NULL,
    first_seen_at     TIMESTAMPTZ NOT NULL,
    last_seen_at      TIMESTAMPTZ NOT NULL,
    status_changed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT difficult_questions_question_key UNIQUE (workspace_id, question_key)
);

CREATE INDEX difficult_questions_status ON difficult_questions (workspace_id, status, last_seen_at DESC);
CREATE INDEX difficult_questions_words ON difficult_questions USING gin (words);

ALTER TABLE difficult_questions ENABLE ROW LEVEL SECURITY;
ALTER TABLE difficult_questions FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON difficult_questions
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());
