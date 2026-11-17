package com.irvinsegura.supportchat.knowledge.application;

import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs ingestion after the upload commits, on a background thread. Deliberately not an
 * {@code @ApplicationModuleListener}: that one opens its transaction before the method body runs, i.e. before the
 * workspace from the event is bound, and row-level security would hide the document.
 * <p>
 * Spring Modulith still records the event in the outbox and marks it completed when this method returns.
 */
@Component
class IngestionListener {

    private final IngestionService ingestion;
    private final IngestionSlots slots;

    IngestionListener(IngestionService ingestion, IngestionSlots slots) {
        this.ingestion = ingestion;
        this.slots = slots;
    }

    @Async
    @TransactionalEventListener
    void on(DocumentQueued event) {
        slots.run(() -> TenantContext.runAs(event.workspaceId(),
                () -> ingestion.process(event.documentId())));
    }
}
