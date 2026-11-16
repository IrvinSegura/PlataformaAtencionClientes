package com.irvinsegura.supportchat.knowledge.domain.model;

public enum DocumentStatus {
    // Uploaded, waiting for the background ingestion
    PENDING,
    // Being extracted, chunked and embedded
    PROCESSING,
    // Searchable
    READY,
    // Ingestion stopped; failureReason says why. Can be processed again
    FAILED
}
