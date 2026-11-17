package com.irvinsegura.supportchat.knowledge.application;

import java.util.concurrent.Semaphore;

/**
 * How many documents one instance processes at the same time. Ingestion runs on virtual threads, so without a bound
 * a site import of thirty pages, or a batch of large spreadsheets, would extract and embed all at once: memory spikes
 * and the embedding provider's rate limit is hit. Waiting documents stay PENDING and cost nothing.
 */
public final class IngestionSlots {

    private final Semaphore permits;

    public IngestionSlots(int maxConcurrent) {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("maxConcurrent must be at least 1");
        }
        this.permits = new Semaphore(maxConcurrent, true);
    }

    void run(Runnable task) {
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // The outbox keeps the event incomplete, so the document is processed again on the next start
            throw new IllegalStateException("Interrupted while waiting to process a document", e);
        }
        try {
            task.run();
        } finally {
            permits.release();
        }
    }
}
