-- Difficult questions close by themselves once they stop costing the capable model for nothing, are matched by
-- meaning as well as by words, and alert the team when they keep coming back.

-- RESOLVED: closed by itself, because the capable model answered it well several times in a row or because it was
-- not found difficult again for a while (see resolution)
ALTER TABLE difficult_questions DROP CONSTRAINT difficult_questions_status_check;
ALTER TABLE difficult_questions
    ADD CONSTRAINT difficult_questions_status_check CHECK (status IN ('OPEN', 'TAUGHT', 'DISMISSED', 'RESOLVED'));

ALTER TABLE difficult_questions
    -- What the question means, embedded like a search query; null for questions recorded before, until asked again
    ADD COLUMN embedding    vector(1024),
    -- Answers the capable model gave it in a row with no sign of difficulty since
    ADD COLUMN good_answers INT         NOT NULL DEFAULT 0,
    -- Why it closed by itself; null unless RESOLVED
    ADD COLUMN resolution   TEXT        CHECK (resolution IN ('ANSWERED_WELL', 'QUIET')),
    -- When the team was told by WhatsApp that it keeps coming back; once per question
    ADD COLUMN alerted_at   TIMESTAMPTZ;

-- Alert when a difficult question keeps coming back; on for workspaces that already alert someone
ALTER TABLE attention_settings ADD COLUMN alert_on_difficult BOOLEAN NOT NULL DEFAULT TRUE;
