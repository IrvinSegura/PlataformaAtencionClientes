package com.irvinsegura.supportchat.knowledge.application;

import com.irvinsegura.supportchat.knowledge.domain.model.Document;
import com.irvinsegura.supportchat.knowledge.domain.model.DocumentStage;
import com.irvinsegura.supportchat.knowledge.domain.model.Embedding;
import com.irvinsegura.supportchat.knowledge.domain.model.EmbeddingUnavailableException;
import com.irvinsegura.supportchat.knowledge.domain.model.EmptyDocumentException;
import com.irvinsegura.supportchat.knowledge.domain.model.InvalidDocumentException;
import com.irvinsegura.supportchat.knowledge.domain.model.KnowledgeLimits;
import com.irvinsegura.supportchat.knowledge.domain.model.TextChunker;
import com.irvinsegura.supportchat.knowledge.domain.model.WebAddress;
import com.irvinsegura.supportchat.knowledge.domain.port.ChunkRepository;
import com.irvinsegura.supportchat.knowledge.domain.port.ChunkRepository.StoredChunk;
import com.irvinsegura.supportchat.knowledge.domain.port.DocumentRepository;
import com.irvinsegura.supportchat.knowledge.domain.port.EmbeddingModel;
import com.irvinsegura.supportchat.knowledge.domain.port.TextExtractor;
import com.irvinsegura.supportchat.knowledge.domain.port.WebPageFetcher;
import com.irvinsegura.supportchat.knowledge.domain.port.WebPageFetcher.FetchedPage;
import com.irvinsegura.supportchat.shared.domain.DomainException;
import com.irvinsegura.supportchat.shared.domain.Ids;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Turns an uploaded file or a web page into searchable passages: claim, download (web pages), extract, chunk, embed,
 * then swap the passages in. Must run with the document's workspace bound. Each step is recorded as the document's
 * stage, and embedding progress after every batch, so the panel can show where a document is.
 * <p>
 * A web page imported with linked pages queues the other pages of the same site it links to, each as a document of
 * its own, so every answer cites the exact page it came from.
 * <p>
 * No transaction is held while the embedding provider is called, which may take seconds; each database step is its
 * own short transaction. Failures end in the FAILED state with a reason the operator can read, and the document
 * can be processed again from the panel.
 */
@Service
public class IngestionService {

    /**
     * A PROCESSING document untouched for this long is assumed abandoned (its instance died) and may be claimed
     * again.
     */
    public static final Duration STALE_PROCESSING_AFTER = Duration.ofMinutes(10);

    // Providers accept far more per request; smaller batches keep each call well under its timeout
    private static final int EMBEDDING_BATCH_SIZE = 64;

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final DocumentRepository documents;
    private final ChunkRepository chunks;
    private final TextExtractor extractor;
    private final WebPageFetcher fetcher;
    private final TextChunker chunker;
    private final EmbeddingModel embeddings;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    IngestionService(DocumentRepository documents, ChunkRepository chunks, TextExtractor extractor,
            WebPageFetcher fetcher, TextChunker chunker, EmbeddingModel embeddings, TransactionTemplate transactions,
            ApplicationEventPublisher events, MeterRegistry meterRegistry, Clock clock) {
        this.documents = documents;
        this.chunks = chunks;
        this.extractor = extractor;
        this.fetcher = fetcher;
        this.chunker = chunker;
        this.embeddings = embeddings;
        this.transactions = transactions;
        this.events = events;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    public void process(UUID documentId) {
        Instant start = clock.instant();
        if (!documents.claimForProcessing(documentId, start, start.minus(STALE_PROCESSING_AFTER))) {
            // Already done, deleted, or another worker has it: nothing to do, and not an error
            return;
        }
        String outcome = "ready";
        try {
            ingest(documentId);
        } catch (DomainException e) {
            outcome = "rejected";
            documents.markFailed(documentId, e.getMessage(), clock.instant());
        } catch (EmbeddingUnavailableException e) {
            outcome = "embedding_unavailable";
            log.warn("Embedding provider unavailable while processing document {}", documentId, e);
            documents.markFailed(documentId, "The embedding service is unavailable; process the document again later",
                    clock.instant());
        } catch (RuntimeException e) {
            outcome = "error";
            log.error("Unexpected error while processing document {}", documentId, e);
            documents.markFailed(documentId, "Unexpected error while processing the document", clock.instant());
        } finally {
            Timer.builder("supportchat.ingestion.duration")
                    .description("Time to extract, chunk and embed one document")
                    .tag("outcome", outcome)
                    .register(meterRegistry)
                    .record(Duration.between(start, clock.instant()));
        }
    }

    private void ingest(UUID documentId) {
        Optional<Document> found = documents.findById(documentId);
        if (found.isEmpty()) {
            return;
        }
        Document document = found.get();
        Optional<byte[]> content = document.isWebPage() ? download(document) : documents.loadContent(documentId);
        if (content.isEmpty() || !stage(documentId, DocumentStage.EXTRACTING, 0, 0)) {
            return;
        }
        List<String> passages = chunker.split(extractor.extract(document.type(), content.get()));
        if (passages.isEmpty()) {
            throw new EmptyDocumentException();
        }
        if (passages.size() > KnowledgeLimits.MAX_CHUNKS_PER_DOCUMENT) {
            throw new InvalidDocumentException("The document has too much text; split it into smaller files");
        }
        Optional<List<StoredChunk>> stored = embedAll(documentId, passages);
        if (stored.isEmpty()) {
            return;
        }
        transactions.executeWithoutResult(status -> {
            chunks.replace(documentId, document.workspaceId(), stored.get());
            // Deleted or reclaimed meanwhile: keep nothing from this run
            if (!documents.markReady(documentId, stored.get().size(), clock.instant())) {
                status.setRollbackOnly();
            }
        });
    }

    /**
     * Downloads the page again, keeps it in place of the last download and queues the pages it links to.
     *
     * @return empty when the document stopped being processed meanwhile
     */
    private Optional<byte[]> download(Document document) {
        if (!stage(document.id(), DocumentStage.FETCHING, 0, 0)) {
            return Optional.empty();
        }
        FetchedPage page = fetcher.fetch(document.sourceUrl());
        if (page.content().length == 0) {
            throw new EmptyDocumentException();
        }
        String title = document.hasAutomaticTitle() ? document.titleFromPage(page.title()) : null;
        if (!documents.storeDownload(document.id(), page.content(), title, clock.instant())) {
            return Optional.empty();
        }
        if (document.linkedPages() > 0) {
            queueLinkedPages(document, page);
        }
        return Optional.of(page.content());
    }

    private void queueLinkedPages(Document document, FetchedPage page) {
        LinkedHashSet<WebAddress> links = new LinkedHashSet<>();
        for (String href : page.links()) {
            page.address().sameSiteLink(href)
                    .filter(link -> !link.equals(document.sourceUrl()))
                    .ifPresent(links::add);
        }
        int queued = 0;
        for (WebAddress link : links) {
            if (queued >= document.linkedPages()) {
                break;
            }
            Document linked = Document.webPage(Ids.newId(), document.workspaceId(), null, link, 0,
                    document.uploadedBy(), clock.instant());
            // Pages already imported (by an earlier import of the site, or by hand) are left as they are. The event
            // is published inside the transaction so the outbox keeps it with the new row
            Boolean inserted = transactions.execute(status -> {
                boolean added = documents.insertWebPage(linked);
                if (added) {
                    events.publishEvent(new DocumentQueued(linked.id(), linked.workspaceId()));
                }
                return added;
            });
            if (Boolean.TRUE.equals(inserted)) {
                queued++;
            }
        }
        log.info("Queued {} linked pages of document {}", queued, document.id());
    }

    private boolean stage(UUID documentId, DocumentStage stage, int embedded, int total) {
        return documents.markStage(documentId, stage, embedded, total, clock.instant());
    }

    /**
     * @return empty when the document stopped being processed meanwhile (e.g. it was deleted)
     */
    private Optional<List<StoredChunk>> embedAll(UUID documentId, List<String> passages) {
        if (!stage(documentId, DocumentStage.INDEXING, 0, passages.size())) {
            return Optional.empty();
        }
        List<StoredChunk> stored = new ArrayList<>(passages.size());
        for (int from = 0; from < passages.size(); from += EMBEDDING_BATCH_SIZE) {
            List<String> batch = passages.subList(from, Math.min(from + EMBEDDING_BATCH_SIZE, passages.size()));
            List<Embedding> vectors = embeddings.embedPassages(batch);
            if (vectors.size() != batch.size()) {
                throw new IllegalStateException("The embedding model returned %d vectors for %d passages"
                        .formatted(vectors.size(), batch.size()));
            }
            for (int i = 0; i < batch.size(); i++) {
                stored.add(new StoredChunk(batch.get(i), vectors.get(i)));
            }
            if (!stage(documentId, DocumentStage.INDEXING, stored.size(), passages.size())) {
                return Optional.empty();
            }
        }
        return Optional.of(stored);
    }
}
