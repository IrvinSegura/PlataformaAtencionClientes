package com.irvinsegura.supportchat.knowledge.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A source of knowledge: an uploaded file or a web page. Its processing state moves PENDING → PROCESSING → READY or
 * FAILED; processing claims and completions are made with conditional updates in the repository, so concurrent
 * workers cannot both process one document. While PROCESSING, its stage and embedded passages show how far it got.
 * <p>
 * A web page has no content until a worker downloads it, and is downloaded again each time it is processed, so
 * processing it again refreshes it.
 */
public final class Document {

    private final UUID id;
    private final UUID workspaceId;
    private final String title;
    private final String fileName;
    private final DocumentType type;
    private final long sizeBytes;
    private final byte[] contentHash;
    private final DocumentStatus status;
    private final String failureReason;
    private final int chunkCount;
    private final UUID uploadedBy;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final WebAddress sourceUrl;
    private final int linkedPages;
    private final DocumentStage stage;
    private final int embeddedChunks;

    private Document(UUID id, UUID workspaceId, String title, String fileName, DocumentType type, long sizeBytes,
            byte[] contentHash, DocumentStatus status, String failureReason, int chunkCount, UUID uploadedBy,
            Instant createdAt, Instant updatedAt, WebAddress sourceUrl, int linkedPages, DocumentStage stage,
            int embeddedChunks) {
        this.id = Objects.requireNonNull(id);
        this.workspaceId = Objects.requireNonNull(workspaceId);
        this.title = title;
        this.fileName = fileName;
        this.type = Objects.requireNonNull(type);
        this.sizeBytes = sizeBytes;
        this.contentHash = contentHash == null ? null : contentHash.clone();
        this.status = Objects.requireNonNull(status);
        this.failureReason = failureReason;
        this.chunkCount = chunkCount;
        this.uploadedBy = Objects.requireNonNull(uploadedBy);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.sourceUrl = sourceUrl;
        this.linkedPages = linkedPages;
        this.stage = stage;
        this.embeddedChunks = embeddedChunks;
    }

    /**
     * A new upload. The title defaults to the file name without its extension.
     */
    public static Document upload(UUID id, UUID workspaceId, String title, String fileName, DocumentType type,
            long sizeBytes, byte[] contentHash, UUID uploadedBy, Instant now) {
        String safeName = sanitizeFileName(fileName);
        String chosenTitle = title == null || title.isBlank() ? stripExtension(safeName) : title.strip();
        if (chosenTitle.isEmpty() || chosenTitle.length() > KnowledgeLimits.MAX_TITLE_LENGTH) {
            throw new InvalidDocumentException(
                    "title must have between 1 and %d characters".formatted(KnowledgeLimits.MAX_TITLE_LENGTH));
        }
        if (sizeBytes <= 0) {
            throw new EmptyDocumentException();
        }
        if (sizeBytes > KnowledgeLimits.MAX_DOCUMENT_BYTES) {
            throw new DocumentTooLargeException();
        }
        return new Document(id, workspaceId, chosenTitle, safeName, type, sizeBytes,
                Objects.requireNonNull(contentHash), DocumentStatus.PENDING, null, 0, uploadedBy, now, now, null, 0,
                null, 0);
    }

    /**
     * A web page to download in the background. Until then its title is its address, unless one was given.
     *
     * @param linkedPages how many other pages of the same site linked from this one to import with it
     */
    public static Document webPage(UUID id, UUID workspaceId, String title, WebAddress address, int linkedPages,
            UUID uploadedBy, Instant now) {
        if (linkedPages < 0 || linkedPages > KnowledgeLimits.MAX_LINKED_PAGES) {
            throw new InvalidDocumentException(
                    "linked pages must be between 0 and %d".formatted(KnowledgeLimits.MAX_LINKED_PAGES));
        }
        String chosenTitle = title == null || title.isBlank() ? automaticTitle(address) : title.strip();
        if (chosenTitle.length() > KnowledgeLimits.MAX_TITLE_LENGTH) {
            throw new InvalidDocumentException(
                    "title must have between 1 and %d characters".formatted(KnowledgeLimits.MAX_TITLE_LENGTH));
        }
        String fileName = address.value().length() > KnowledgeLimits.MAX_FILE_NAME_LENGTH
                ? address.value().substring(0, KnowledgeLimits.MAX_FILE_NAME_LENGTH)
                : address.value();
        return new Document(id, workspaceId, chosenTitle, fileName, DocumentType.HTML, 0, null,
                DocumentStatus.PENDING, null, 0, uploadedBy, now, now, address, linkedPages, null, 0);
    }

    public static Document restore(UUID id, UUID workspaceId, String title, String fileName, DocumentType type,
            long sizeBytes, byte[] contentHash, DocumentStatus status, String failureReason, int chunkCount,
            UUID uploadedBy, Instant createdAt, Instant updatedAt, WebAddress sourceUrl, int linkedPages,
            DocumentStage stage, int embeddedChunks) {
        return new Document(id, workspaceId, title, fileName, type, sizeBytes, contentHash, status, failureReason,
                chunkCount, uploadedBy, createdAt, updatedAt, sourceUrl, linkedPages, stage, embeddedChunks);
    }

    public boolean isWebPage() {
        return sourceUrl != null;
    }

    /**
     * Whether the title is still the one given by default, so the page's own title may replace it once downloaded.
     */
    public boolean hasAutomaticTitle() {
        return isWebPage() && title.equals(automaticTitle(sourceUrl));
    }

    /**
     * The page's own title, cut to fit, or the current title when the page has none.
     */
    public String titleFromPage(String pageTitle) {
        String cleaned = pageTitle == null ? "" : pageTitle.replaceAll("[\\p{Cntrl}]", " ").strip();
        if (cleaned.isEmpty()) {
            return title;
        }
        return cleaned.length() > KnowledgeLimits.MAX_TITLE_LENGTH
                ? cleaned.substring(0, KnowledgeLimits.MAX_TITLE_LENGTH)
                : cleaned;
    }

    private static String automaticTitle(WebAddress address) {
        return address.value().length() > KnowledgeLimits.MAX_TITLE_LENGTH
                ? address.value().substring(0, KnowledgeLimits.MAX_TITLE_LENGTH)
                : address.value();
    }

    /**
     * Whether it may be queued again. A document being processed right now may not, unless its worker has been
     * silent for longer than {@code staleAfter} (it most likely died with the instance running it).
     */
    public boolean canBeReprocessed(Instant now, Duration staleAfter) {
        return status != DocumentStatus.PROCESSING || updatedAt.plus(staleAfter).isBefore(now);
    }

    // Keeps only the last path segment and printable characters: browsers may send full paths, and the name is
    // shown back in the panel
    private static String sanitizeFileName(String fileName) {
        String name = fileName == null ? "" : fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
        if (name.isEmpty()) {
            name = "document";
        }
        return name.length() > KnowledgeLimits.MAX_FILE_NAME_LENGTH
                ? name.substring(name.length() - KnowledgeLimits.MAX_FILE_NAME_LENGTH)
                : name;
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        return base.length() > KnowledgeLimits.MAX_TITLE_LENGTH
                ? base.substring(0, KnowledgeLimits.MAX_TITLE_LENGTH)
                : base;
    }

    public UUID id() {
        return id;
    }

    public UUID workspaceId() {
        return workspaceId;
    }

    public String title() {
        return title;
    }

    public String fileName() {
        return fileName;
    }

    public DocumentType type() {
        return type;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    /**
     * Null for web pages, whose content changes between downloads.
     */
    public byte[] contentHash() {
        return contentHash == null ? null : contentHash.clone();
    }

    public DocumentStatus status() {
        return status;
    }

    public String failureReason() {
        return failureReason;
    }

    public int chunkCount() {
        return chunkCount;
    }

    public UUID uploadedBy() {
        return uploadedBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /**
     * The page's address, or null for an uploaded file.
     */
    public WebAddress sourceUrl() {
        return sourceUrl;
    }

    public int linkedPages() {
        return linkedPages;
    }

    /**
     * Null unless PROCESSING.
     */
    public DocumentStage stage() {
        return stage;
    }

    public int embeddedChunks() {
        return embeddedChunks;
    }
}
