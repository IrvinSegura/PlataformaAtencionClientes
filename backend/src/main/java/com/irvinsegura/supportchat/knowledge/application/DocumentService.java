package com.irvinsegura.supportchat.knowledge.application;

import com.irvinsegura.supportchat.knowledge.domain.model.Document;
import com.irvinsegura.supportchat.knowledge.domain.model.DocumentBusyException;
import com.irvinsegura.supportchat.knowledge.domain.model.DocumentNotFoundException;
import com.irvinsegura.supportchat.knowledge.domain.model.DocumentType;
import com.irvinsegura.supportchat.knowledge.domain.model.DuplicateWebPageException;
import com.irvinsegura.supportchat.knowledge.domain.model.WebAddress;
import com.irvinsegura.supportchat.knowledge.domain.port.DocumentRepository;
import com.irvinsegura.supportchat.shared.domain.Ids;
import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operators' view of the knowledge base: upload, list, inspect, delete and process documents again.
 */
@Service
public class DocumentService {

    private final DocumentRepository documents;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    DocumentService(DocumentRepository documents, ApplicationEventPublisher events, Clock clock) {
        this.documents = documents;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Stores the file and queues it; text extraction and embedding happen in the background.
     */
    @Transactional
    public DocumentView upload(String title, String fileName, byte[] content, UUID uploadedBy) {
        UUID workspaceId = TenantContext.requireWorkspaceId();
        DocumentType type = DocumentType.detect(fileName, content);
        Document document = Document.upload(Ids.newId(), workspaceId, title, fileName, type, content.length,
                sha256(content), uploadedBy, clock.instant());
        documents.insert(document, content);
        events.publishEvent(new DocumentQueued(document.id(), workspaceId));
        return DocumentView.of(document);
    }

    /**
     * Queues a web page to be downloaded and learned in the background, optionally with the pages of the same site it
     * links to, so a business with a website but no tidy documents can teach the assistant by pasting one address.
     *
     * @param linkedPages how many linked pages of the same site to import too, 0 for this page only
     */
    @Transactional
    public DocumentView importWebPage(String url, String title, int linkedPages, UUID uploadedBy) {
        UUID workspaceId = TenantContext.requireWorkspaceId();
        Document document = Document.webPage(Ids.newId(), workspaceId, title, WebAddress.parse(url), linkedPages,
                uploadedBy, clock.instant());
        if (!documents.insertWebPage(document)) {
            throw new DuplicateWebPageException();
        }
        events.publishEvent(new DocumentQueued(document.id(), workspaceId));
        return DocumentView.of(document);
    }

    @Transactional(readOnly = true)
    public DocumentPageView page(int page, int size) {
        DocumentRepository.DocumentPage result = documents.page(page, size);
        return new DocumentPageView(result.documents().stream().map(DocumentView::of).toList(), page, size,
                result.total());
    }

    @Transactional(readOnly = true)
    public DocumentView get(UUID documentId) {
        return DocumentView.of(load(documentId));
    }

    /**
     * Deletes the document with its file and passages; the assistant stops quoting it at once.
     */
    @Transactional
    public void delete(UUID documentId) {
        if (!documents.delete(documentId)) {
            throw new DocumentNotFoundException();
        }
    }

    /**
     * Queues the document again, e.g. after a failure or once a better embedding model is configured. A web page is
     * downloaded again, so this also refreshes it. The current passages stay searchable until the new ones replace
     * them.
     */
    @Transactional
    public DocumentView reprocess(UUID documentId) {
        Document document = load(documentId);
        Instant now = clock.instant();
        if (!document.canBeReprocessed(now, IngestionService.STALE_PROCESSING_AFTER)) {
            throw new DocumentBusyException();
        }
        documents.requeue(documentId, now);
        events.publishEvent(new DocumentQueued(documentId, document.workspaceId()));
        return DocumentView.of(load(documentId));
    }

    private Document load(UUID documentId) {
        return documents.findById(documentId).orElseThrow(DocumentNotFoundException::new);
    }

    private static byte[] sha256(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required on every Java platform", e);
        }
    }

    public record DocumentPageView(List<DocumentView> items, int page, int size, long total) {
    }
}
