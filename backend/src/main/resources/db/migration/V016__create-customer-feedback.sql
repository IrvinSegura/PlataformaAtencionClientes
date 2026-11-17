-- What customers think of the help they got: a thumbs up or down on each answer of the assistant, and a rating from
-- 1 to 5 of the whole conversation. Both can be changed, so each is one row updated in place.

CREATE TABLE answer_feedback (
    message_id      UUID        PRIMARY KEY REFERENCES conversation_messages (id) ON DELETE CASCADE,
    workspace_id    UUID        NOT NULL,
    conversation_id UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    helpful         BOOLEAN     NOT NULL,
    given_at        TIMESTAMPTZ NOT NULL
);

CREATE INDEX answer_feedback_conversation_idx ON answer_feedback (conversation_id);
CREATE INDEX answer_feedback_workspace_idx ON answer_feedback (workspace_id, given_at);

CREATE TABLE conversation_ratings (
    conversation_id UUID        PRIMARY KEY REFERENCES conversations (id) ON DELETE CASCADE,
    workspace_id    UUID        NOT NULL,
    score           SMALLINT    NOT NULL,
    comment         TEXT,
    rated_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT conversation_ratings_score_check CHECK (score BETWEEN 1 AND 5),
    CONSTRAINT conversation_ratings_comment_check CHECK (char_length(comment) <= 1000)
);

CREATE INDEX conversation_ratings_workspace_idx ON conversation_ratings (workspace_id, rated_at DESC);

-- The outcomes report counts the conversations started in a period
CREATE INDEX conversations_workspace_started_idx ON conversations (workspace_id, started_at);

ALTER TABLE answer_feedback ENABLE ROW LEVEL SECURITY;
ALTER TABLE answer_feedback FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON answer_feedback
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());

ALTER TABLE conversation_ratings ENABLE ROW LEVEL SECURITY;
ALTER TABLE conversation_ratings FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON conversation_ratings
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());
