package com.irvinsegura.supportchat.knowledge.application;

import com.irvinsegura.supportchat.knowledge.domain.model.Document;
import com.irvinsegura.supportchat.knowledge.domain.model.DocumentStage;
import com.irvinsegura.supportchat.knowledge.domain.model.DocumentStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * @param sourceUrl      the page's address for an imported web page, otherwise null
 * @param stage          what a PROCESSING document is doing, otherwise null
 * @param chunkCount     passages of a READY document; while INDEXING, the passages being embedded
 * @param embeddedChunks passages embedded so far while INDEXING
 */
public record DocumentView(UUID id, String title, String fileName, String mediaType, long sizeBytes,
        DocumentStatus status, @Nullable String failureReason, int chunkCount, Instant createdAt, Instant updatedAt,
        @Nullable String sourceUrl, @Nullable DocumentStage stage, int embeddedChunks) {

    static DocumentView of(Document document) {
        return new DocumentView(document.id(), document.title(), document.fileName(), document.type().mediaType(),
                document.sizeBytes(), document.status(), document.failureReason(), document.chunkCount(),
                document.createdAt(), document.updatedAt(),
                document.sourceUrl() == null ? null : document.sourceUrl().value(), document.stage(),
                document.embeddedChunks());
    }
}
