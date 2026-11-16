package com.irvinsegura.supportchat.knowledge.domain.model;

/**
 * Where a PROCESSING document is, so the panel can show its progress step by step.
 */
public enum DocumentStage {
    // A web page is being downloaded
    FETCHING,
    // Text is being extracted from the file and split into passages
    EXTRACTING,
    // Passages are being embedded; the document's progress counts how many are done
    INDEXING
}
