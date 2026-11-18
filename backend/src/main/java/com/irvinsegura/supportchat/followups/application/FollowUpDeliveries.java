package com.irvinsegura.supportchat.followups.application;

import com.irvinsegura.supportchat.channels.ReplyNotDelivered;
import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Follows what the channels say about delivery: a follow-up counts as sent when it is handed to the channel, so one
 * the channel then refuses has to be marked as failed.
 */
@Component
class FollowUpDeliveries {

    private final FollowUpService followUps;

    FollowUpDeliveries(FollowUpService followUps) {
        this.followUps = followUps;
    }

    @Async
    @TransactionalEventListener
    void on(ReplyNotDelivered undelivered) {
        TenantContext.runAs(undelivered.workspaceId(),
                () -> followUps.notDelivered(undelivered.messageId(), undelivered.reason()));
    }
}
