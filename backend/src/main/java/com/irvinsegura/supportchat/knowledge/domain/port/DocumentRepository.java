package com.irvinsegura.supportchat.knowledge.domain.port;

import com.irvinsegura.supportchat.knowledge.domain.model.Document;
import com.irvinsegura.supportchat.knowledge.domain.model.DocumentStage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Documents of the workspace bound to the current transaction.
 */
public interface DocumentRepository {

    /**
     * @throws com.irvinsegura.supportchat.knowledge.domain.model.DuplicateDocumentException if the workspace already
     *                                                                                       has a file with this content
     */
    void insert(Document document, byte[] content);

    /**
     * Adds a web page with no content yet.
     *
     * @return false when the workspace already has a page with this address
     */
    boolean insertWebPage(Document document);

    Optional<Document> findById(UUID id);

    DocumentPage page(int page, int size);

    boolean delete(UUID id);

    /**
     * A digest of every document's id and last change; any upload, processing, rename or deletion changes it.
     */
    String revision();

    Optional<byte[]> loadContent(UUID id);

    /**
     * Moves a PENDING document, or a PROCESSING one whose worker went silent before {@code staleBefore}, to
     * PROCESSING. Only one caller can win the claim.
     *
     * @return whether this caller now owns the processing
     */
    boolean claimForProcessing(UUID id, Instant now, Instant staleBefore);

    /**
     * Records how far a PROCESSING document got. Also refreshes its last update, so a long but healthy run is never
     * taken for an abandoned one.
     *
     * @param totalChunks passages the document was split into, or 0 before it is known
     * @return false when the document is no longer PROCESSING
     */
    boolean markStage(UUID id, DocumentStage stage, int embeddedChunks, int totalChunks, Instant now);

    /**
     * Keeps a downloaded web page's content in place of the previous download.
     *
     * @param title the new title, or null to keep the current one
     * @return false when the document is no longer PROCESSING
     */
    boolean storeDownload(UUID id, byte[] content, String title, Instant now);

    /**
     * @return false when the document is no longer PROCESSING (deleted, or claimed again by another worker)
     */
    boolean markReady(UUID id, int chunkCount, Instant now);

    void markFailed(UUID id, String reason, Instant now);

    void requeue(UUID id, Instant now);

    record DocumentPage(List<Document> documents, long total) {
    }
}
