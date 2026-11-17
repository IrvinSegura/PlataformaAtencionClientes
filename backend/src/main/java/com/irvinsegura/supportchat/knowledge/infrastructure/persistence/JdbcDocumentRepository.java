package com.irvinsegura.supportchat.knowledge.infrastructure.persistence;

import com.irvinsegura.supportchat.knowledge.domain.model.Document;
import com.irvinsegura.supportchat.knowledge.domain.model.DocumentStage;
import com.irvinsegura.supportchat.knowledge.domain.model.DocumentStatus;
import com.irvinsegura.supportchat.knowledge.domain.model.DocumentType;
import com.irvinsegura.supportchat.knowledge.domain.model.DuplicateDocumentException;
import com.irvinsegura.supportchat.knowledge.domain.model.WebAddress;
import com.irvinsegura.supportchat.knowledge.domain.port.DocumentRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every method is transactional because row-level security only sees the workspace inside a transaction. No query
 * filters by workspace itself: the policies do, so a missing condition here cannot leak another workspace's rows.
 */
@Repository
class JdbcDocumentRepository implements DocumentRepository {

    private static final String COLUMNS = """
            id, workspace_id, title, file_name, media_type, size_bytes, content_hash, status, failure_reason,
            chunk_count, uploaded_by, created_at, updated_at, source_url, linked_pages, processing_stage,
            embedded_chunks""";

    // Failure reasons are shown to operators; long provider messages are cut, never stored whole
    private static final int MAX_FAILURE_REASON_LENGTH = 500;

    private final JdbcClient jdbc;

    JdbcDocumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void insert(Document document, byte[] content) {
        try {
            jdbc.sql("""
                            INSERT INTO documents (id, workspace_id, title, file_name, media_type, size_bytes,
                                                   content_hash, status, chunk_count, uploaded_by, created_at,
                                                   updated_at)
                            VALUES (:id, :workspaceId, :title, :fileName, :mediaType, :sizeBytes, :contentHash,
                                    :status, 0, :uploadedBy, :createdAt, :updatedAt)
                            """)
                    .param("id", document.id())
                    .param("workspaceId", document.workspaceId())
                    .param("title", document.title())
                    .param("fileName", document.fileName())
                    .param("mediaType", document.type().mediaType())
                    .param("sizeBytes", document.sizeBytes())
                    .param("contentHash", document.contentHash())
                    .param("status", document.status().name())
                    .param("uploadedBy", document.uploadedBy())
                    .param("createdAt", Timestamp.from(document.createdAt()))
                    .param("updatedAt", Timestamp.from(document.updatedAt()))
                    .update();
        } catch (DuplicateKeyException e) {
            throw new DuplicateDocumentException();
        }
        jdbc.sql("INSERT INTO document_files (document_id, workspace_id, content) VALUES (:id, :workspaceId, :content)")
                .param("id", document.id())
                .param("workspaceId", document.workspaceId())
                .param("content", content)
                .update();
    }

    @Override
    @Transactional
    public boolean insertWebPage(Document document) {
        return jdbc.sql("""
                        INSERT INTO documents (id, workspace_id, title, file_name, media_type, size_bytes,
                                               content_hash, status, chunk_count, uploaded_by, created_at,
                                               updated_at, source_url, linked_pages)
                        VALUES (:id, :workspaceId, :title, :fileName, :mediaType, 0, NULL, :status, 0, :uploadedBy,
                                :createdAt, :updatedAt, :sourceUrl, :linkedPages)
                        ON CONFLICT (workspace_id, source_url) WHERE source_url IS NOT NULL DO NOTHING
                        """)
                .param("id", document.id())
                .param("workspaceId", document.workspaceId())
                .param("title", document.title())
                .param("fileName", document.fileName())
                .param("mediaType", document.type().mediaType())
                .param("status", document.status().name())
                .param("uploadedBy", document.uploadedBy())
                .param("createdAt", Timestamp.from(document.createdAt()))
                .param("updatedAt", Timestamp.from(document.updatedAt()))
                .param("sourceUrl", document.sourceUrl().value())
                .param("linkedPages", document.linkedPages())
                .update() == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Document> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM documents WHERE id = :id")
                .param("id", id)
                .query(JdbcDocumentRepository::map)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentPage page(int page, int size) {
        List<Document> documents = jdbc.sql("SELECT " + COLUMNS
                        + " FROM documents ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset")
                .param("limit", size)
                .param("offset", (long) page * size)
                .query(JdbcDocumentRepository::map)
                .list();
        long total = jdbc.sql("SELECT count(*) FROM documents").query(Long.class).single();
        return new DocumentPage(documents, total);
    }

    @Override
    @Transactional(readOnly = true)
    public String revision() {
        // Every write to a document moves its updated_at, and a deletion drops it from the list
        return jdbc.sql("""
                        SELECT md5(COALESCE(string_agg(id::text || '@' || updated_at::text, ',' ORDER BY id), ''))
                        FROM documents
                        """)
                .query(String.class)
                .single();
    }

    @Override
    @Transactional
    public boolean delete(UUID id) {
        // Files and passages go with it (ON DELETE CASCADE)
        return jdbc.sql("DELETE FROM documents WHERE id = :id").param("id", id).update() == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<byte[]> loadContent(UUID id) {
        return jdbc.sql("SELECT content FROM document_files WHERE document_id = :id")
                .param("id", id)
                .query((rs, row) -> rs.getBytes("content"))
                .optional();
    }

    @Override
    @Transactional
    public boolean claimForProcessing(UUID id, Instant now, Instant staleBefore) {
        return jdbc.sql("""
                        UPDATE documents
                        SET status = 'PROCESSING', failure_reason = NULL, processing_stage = NULL,
                            embedded_chunks = 0, updated_at = :now
                        WHERE id = :id
                          AND (status = 'PENDING' OR (status = 'PROCESSING' AND updated_at < :staleBefore))
                        """)
                .param("id", id)
                .param("now", Timestamp.from(now))
                .param("staleBefore", Timestamp.from(staleBefore))
                .update() == 1;
    }

    @Override
    @Transactional
    public boolean markStage(UUID id, DocumentStage stage, int embeddedChunks, int totalChunks, Instant now) {
        // The passage count is only replaced once it is known, so a document processed again keeps showing its
        // previous count while it is being downloaded or read
        return jdbc.sql("""
                        UPDATE documents
                        SET processing_stage = :stage, embedded_chunks = :embedded,
                            chunk_count = CASE WHEN :total > 0 THEN :total ELSE chunk_count END, updated_at = :now
                        WHERE id = :id AND status = 'PROCESSING'
                        """)
                .param("id", id)
                .param("stage", stage.name())
                .param("embedded", embeddedChunks)
                .param("total", totalChunks)
                .param("now", Timestamp.from(now))
                .update() == 1;
    }

    @Override
    @Transactional
    public boolean storeDownload(UUID id, byte[] content, String title, Instant now) {
        boolean processing = jdbc.sql("""
                        UPDATE documents
                        SET size_bytes = :size, title = COALESCE(:title, title), updated_at = :now
                        WHERE id = :id AND status = 'PROCESSING'
                        """)
                .param("id", id)
                .param("size", (long) content.length)
                .param("title", title)
                .param("now", Timestamp.from(now))
                .update() == 1;
        if (!processing) {
            return false;
        }
        jdbc.sql("""
                        INSERT INTO document_files (document_id, workspace_id, content)
                        SELECT id, workspace_id, :content FROM documents WHERE id = :id
                        ON CONFLICT (document_id) DO UPDATE SET content = EXCLUDED.content
                        """)
                .param("id", id)
                .param("content", content)
                .update();
        return true;
    }

    @Override
    @Transactional
    public boolean markReady(UUID id, int chunkCount, Instant now) {
        return jdbc.sql("""
                        UPDATE documents
                        SET status = 'READY', chunk_count = :chunkCount, failure_reason = NULL,
                            processing_stage = NULL, embedded_chunks = :chunkCount, updated_at = :now
                        WHERE id = :id AND status = 'PROCESSING'
                        """)
                .param("id", id)
                .param("chunkCount", chunkCount)
                .param("now", Timestamp.from(now))
                .update() == 1;
    }

    @Override
    @Transactional
    public void markFailed(UUID id, String reason, Instant now) {
        String trimmed = reason == null ? "Unknown error" : reason;
        if (trimmed.length() > MAX_FAILURE_REASON_LENGTH) {
            trimmed = trimmed.substring(0, MAX_FAILURE_REASON_LENGTH);
        }
        jdbc.sql("""
                        UPDATE documents
                        SET status = 'FAILED', failure_reason = :reason, processing_stage = NULL, updated_at = :now
                        WHERE id = :id AND status = 'PROCESSING'
                        """)
                .param("id", id)
                .param("reason", trimmed)
                .param("now", Timestamp.from(now))
                .update();
    }

    @Override
    @Transactional
    public void requeue(UUID id, Instant now) {
        jdbc.sql("""
                        UPDATE documents
                        SET status = 'PENDING', failure_reason = NULL, processing_stage = NULL, updated_at = :now
                        WHERE id = :id
                        """)
                .param("id", id)
                .param("now", Timestamp.from(now))
                .update();
    }

    private static Document map(ResultSet rs, int row) throws SQLException {
        return Document.restore(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getString("title"),
                rs.getString("file_name"),
                DocumentType.fromMediaType(rs.getString("media_type")),
                rs.getLong("size_bytes"),
                rs.getBytes("content_hash"),
                DocumentStatus.valueOf(rs.getString("status")),
                rs.getString("failure_reason"),
                rs.getInt("chunk_count"),
                rs.getObject("uploaded_by", UUID.class),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getString("source_url") == null ? null : new WebAddress(rs.getString("source_url")),
                rs.getInt("linked_pages"),
                rs.getString("processing_stage") == null ? null
                        : DocumentStage.valueOf(rs.getString("processing_stage")),
                rs.getInt("embedded_chunks"));
    }
}
