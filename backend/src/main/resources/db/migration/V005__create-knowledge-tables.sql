-- Knowledge base. Every table carries workspace_id and is protected by row-level security, so a query can only ever
-- see the workspace bound to its transaction, whatever its WHERE clause says. FORCE applies the policies to the
-- table owner too; only superusers bypass them.

CREATE TABLE documents (
    id             UUID        PRIMARY KEY,
    workspace_id   UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    title          TEXT        NOT NULL,
    file_name      TEXT        NOT NULL,
    media_type     TEXT        NOT NULL,
    size_bytes     BIGINT      NOT NULL,
    content_hash   BYTEA       NOT NULL,
    status         TEXT        NOT NULL,
    failure_reason TEXT,
    chunk_count    INT         NOT NULL DEFAULT 0,
    uploaded_by    UUID        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    -- The same file uploaded twice would only duplicate search results
    CONSTRAINT documents_workspace_content_hash_key UNIQUE (workspace_id, content_hash),
    CONSTRAINT documents_status_check CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED'))
);

CREATE INDEX documents_workspace_created_idx ON documents (workspace_id, created_at DESC);

-- The uploaded bytes, kept so a document can be processed again (new chunking or embedding model) without asking
-- for the file. Separate from documents so listing documents never reads them.
CREATE TABLE document_files (
    document_id  UUID  PRIMARY KEY REFERENCES documents (id) ON DELETE CASCADE,
    workspace_id UUID  NOT NULL,
    content      BYTEA NOT NULL
);

CREATE TABLE document_chunks (
    id            UUID         PRIMARY KEY,
    workspace_id  UUID         NOT NULL,
    document_id   UUID         NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    chunk_index   INT          NOT NULL,
    content       TEXT         NOT NULL,
    embedding     vector(1024) NOT NULL,
    -- The 'simple' configuration neither stems nor drops stop words: the keyword retriever exists to catch exact
    -- terms (product codes, names, prices) that embeddings blur, while meaning is the vector retriever's job
    search_vector TSVECTOR     GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED,
    CONSTRAINT document_chunks_document_index_key UNIQUE (document_id, chunk_index)
);

CREATE INDEX document_chunks_embedding_idx ON document_chunks USING hnsw (embedding vector_cosine_ops);
CREATE INDEX document_chunks_search_vector_idx ON document_chunks USING gin (search_vector);
CREATE INDEX document_chunks_workspace_idx ON document_chunks (workspace_id);

ALTER TABLE documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE documents FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON documents
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());

ALTER TABLE document_files ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_files FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON document_files
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());

ALTER TABLE document_chunks ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_chunks FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON document_chunks
    USING (workspace_id = current_workspace_id())
    WITH CHECK (workspace_id = current_workspace_id());
