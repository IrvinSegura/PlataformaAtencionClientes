-- pgvector stores embeddings and searches them by cosine distance (0.8 or later for iterative index scans)
CREATE EXTENSION IF NOT EXISTS vector;

-- The workspace the current transaction runs for, set by the application at the start of every transaction.
-- Empty or unset means none: policies then match no rows, so a missing binding fails closed.
CREATE FUNCTION current_workspace_id() RETURNS UUID
    LANGUAGE sql
    STABLE
    PARALLEL SAFE
AS $$
SELECT NULLIF(current_setting('app.workspace_id', true), '')::UUID
$$;
