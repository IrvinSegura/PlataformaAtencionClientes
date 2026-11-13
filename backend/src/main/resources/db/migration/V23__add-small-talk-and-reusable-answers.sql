-- Two ways to answer without paying for a model call: a fixed reply to a greeting or a thanks, and the answer the
-- model already wrote to the same question

-- A greeting, thanks or goodbye answered with a fixed courtesy reply
ALTER TABLE conversation_messages DROP CONSTRAINT conversation_messages_outcome_check;
ALTER TABLE conversation_messages ADD CONSTRAINT conversation_messages_outcome_check
    CHECK (outcome IN ('ANSWERED', 'NO_ANSWER', 'FAILED', 'SMALL_TALK'));

-- Answers to questions asked without earlier context, kept to answer the same question again. The context digest
-- covers the knowledge base, the actions and the assistant's name, so a change to any of them makes old rows miss.
CREATE TABLE reusable_answers (
    workspace_id   UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    -- The question folded to lower case, without accents or punctuation
    question_key   TEXT        NOT NULL,
    -- The model choice of the channel: each one keeps its own answer
    preference     TEXT        NOT NULL,
    context_digest TEXT        NOT NULL,
    answer         TEXT        NOT NULL,
    citations      JSONB       NOT NULL,
    model          TEXT        NOT NULL,
    model_tier     TEXT        CHECK (model_tier IN ('SIMPLE', 'COMPLEX')),
    created_at     TIMESTAMPTZ NOT NULL,
    reuses         INT         NOT NULL DEFAULT 0,
    last_reused_at TIMESTAMPTZ,
    PRIMARY KEY (workspace_id, question_key, preference, context_digest)
);

CREATE INDEX reusable_answers_created ON reusable_answers (workspace_id, created_at);

ALTER TABLE reusable_answers ENABLE ROW LEVEL SECURITY;
ALTER TABLE reusable_answers FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON reusable_answers
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());
