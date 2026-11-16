package com.irvinsegura.supportchat.knowledge.domain.model;

/**
 * Bounds that keep one upload from exhausting memory, storage or the embedding budget.
 */
public final class KnowledgeLimits {

    public static final long MAX_DOCUMENT_BYTES = 10L * 1024 * 1024;
    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_FILE_NAME_LENGTH = 255;
    // A scanned 500-page PDF has no text anyway; a malicious one may declare millions of pages
    public static final int MAX_PDF_PAGES = 500;
    // About 1.5 million characters with the default chunk size
    public static final int MAX_CHUNKS_PER_DOCUMENT = 2_000;
    // Text taken from one file or page; above this it would not fit in MAX_CHUNKS_PER_DOCUMENT anyway, and stopping
    // early keeps a decompression bomb from filling memory
    public static final int MAX_EXTRACTED_CHARS = 2_000_000;
    public static final long MAX_WEB_PAGE_BYTES = 3L * 1024 * 1024;
    public static final int MAX_URL_LENGTH = 2_048;
    // Pages of the same site found from an imported page and imported with it
    public static final int MAX_LINKED_PAGES = 30;
    // Fixed by the vector(1024) column; every embedding model must produce exactly this many dimensions
    public static final int EMBEDDING_DIMENSIONS = 1024;

    private KnowledgeLimits() {
    }
}
