package com.irvinsegura.supportchat.channels.application;

import com.irvinsegura.supportchat.channels.ReplyNotDelivered;
import com.irvinsegura.supportchat.channels.ReplyNotDelivered.Reason;
import com.irvinsegura.supportchat.conversations.ConversationReply;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tells the other modules that a reply will never reach the customer, e.g. so a follow-up stops showing as sent.
 */
@Component
class UndeliveredReplies {

    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;

    UndeliveredReplies(ApplicationEventPublisher events, PlatformTransactionManager transactionManager) {
        this.events = events;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    void report(ConversationReply reply, Reason reason) {
        if (reply.messageId() == null) {
            return;
        }
        // Published in a transaction, so the listeners get it through the outbox like any other event
        transactions.executeWithoutResult(status -> events.publishEvent(new ReplyNotDelivered(reply.workspaceId(),
                reply.conversationId(), reply.messageId(), reason)));
    }
}
