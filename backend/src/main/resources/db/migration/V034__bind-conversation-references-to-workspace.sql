-- A foreign key is checked without row-level security, so a row of one workspace could point at a conversation of
-- another. Each reference to a conversation now names the workspace too, and the database refuses a mix.
ALTER TABLE conversations
    ADD CONSTRAINT conversations_workspace_id_key UNIQUE (workspace_id, id);

ALTER TABLE conversation_messages
    DROP CONSTRAINT conversation_messages_conversation_id_fkey,
    ADD CONSTRAINT conversation_messages_conversation_fkey FOREIGN KEY (workspace_id, conversation_id)
        REFERENCES conversations (workspace_id, id) ON DELETE CASCADE;

ALTER TABLE knowledge_gap_occurrences
    DROP CONSTRAINT knowledge_gap_occurrences_conversation_id_fkey,
    ADD CONSTRAINT knowledge_gap_occurrences_conversation_fkey FOREIGN KEY (workspace_id, conversation_id)
        REFERENCES conversations (workspace_id, id) ON DELETE CASCADE;

ALTER TABLE action_runs
    DROP CONSTRAINT action_runs_conversation_id_fkey,
    ADD CONSTRAINT action_runs_conversation_fkey FOREIGN KEY (workspace_id, conversation_id)
        REFERENCES conversations (workspace_id, id) ON DELETE CASCADE;

ALTER TABLE answer_feedback
    DROP CONSTRAINT answer_feedback_conversation_id_fkey,
    ADD CONSTRAINT answer_feedback_conversation_fkey FOREIGN KEY (workspace_id, conversation_id)
        REFERENCES conversations (workspace_id, id) ON DELETE CASCADE;

ALTER TABLE conversation_ratings
    DROP CONSTRAINT conversation_ratings_conversation_id_fkey,
    ADD CONSTRAINT conversation_ratings_conversation_fkey FOREIGN KEY (workspace_id, conversation_id)
        REFERENCES conversations (workspace_id, id) ON DELETE CASCADE;

ALTER TABLE attention_alerts
    DROP CONSTRAINT attention_alerts_conversation_id_fkey,
    ADD CONSTRAINT attention_alerts_conversation_fkey FOREIGN KEY (workspace_id, conversation_id)
        REFERENCES conversations (workspace_id, id) ON DELETE CASCADE;

-- Only the conversation is cleared when it is deleted; the question stays with its workspace
ALTER TABLE difficult_questions
    DROP CONSTRAINT difficult_questions_conversation_id_fkey,
    ADD CONSTRAINT difficult_questions_conversation_fkey FOREIGN KEY (workspace_id, conversation_id)
        REFERENCES conversations (workspace_id, id) ON DELETE SET NULL (conversation_id);

ALTER TABLE follow_ups
    DROP CONSTRAINT follow_ups_conversation_id_fkey,
    ADD CONSTRAINT follow_ups_conversation_fkey FOREIGN KEY (workspace_id, conversation_id)
        REFERENCES conversations (workspace_id, id) ON DELETE CASCADE;
