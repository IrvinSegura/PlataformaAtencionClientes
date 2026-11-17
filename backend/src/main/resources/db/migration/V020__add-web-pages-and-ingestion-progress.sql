-- Web pages as knowledge, and the progress of a document being processed.
--
-- A web page has no file until a worker downloads it, and its content changes between downloads, so it has no
-- content hash; it is told apart from other pages by its address instead.

ALTER TABLE documents ALTER COLUMN content_hash DROP NOT NULL;

ALTER TABLE documents
    ADD COLUMN source_url       TEXT,
    -- Other pages of the same site linked from this one, imported with it when it is downloaded
    ADD COLUMN linked_pages     INT NOT NULL DEFAULT 0,
    ADD COLUMN processing_stage TEXT,
    ADD COLUMN embedded_chunks  INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT documents_processing_stage_check
        CHECK (processing_stage IN ('FETCHING', 'EXTRACTING', 'INDEXING')),
    ADD CONSTRAINT documents_source_check
        CHECK (source_url IS NOT NULL OR content_hash IS NOT NULL),
    ADD CONSTRAINT documents_linked_pages_check CHECK (linked_pages BETWEEN 0 AND 30);

CREATE UNIQUE INDEX documents_workspace_source_url_key ON documents (workspace_id, source_url)
    WHERE source_url IS NOT NULL;
