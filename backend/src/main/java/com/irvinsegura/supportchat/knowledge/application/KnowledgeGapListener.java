package com.irvinsegura.supportchat.knowledge.application;

import com.irvinsegura.supportchat.knowledge.QuestionUnanswered;
import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Feeds the knowledge gaps report after an unanswered question is saved, off the visitor's request. Binds the
 * workspace before any transaction starts, for the same reason as the ingestion listener; the outbox delivers the
 * event again if the process stops first, and recording is idempotent.
 */
@Component
class KnowledgeGapListener {

    private final KnowledgeGapService gaps;

    KnowledgeGapListener(KnowledgeGapService gaps) {
        this.gaps = gaps;
    }

    @Async
    @TransactionalEventListener
    void on(QuestionUnanswered event) {
        TenantContext.runAs(event.workspaceId(), () -> gaps.record(event));
    }
}
