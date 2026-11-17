package com.irvinsegura.supportchat.conversations.application;

import com.irvinsegura.supportchat.conversations.application.ConversationViews.PendingActionView;
import com.irvinsegura.supportchat.conversations.domain.model.AnswerOutcome;
import com.irvinsegura.supportchat.conversations.domain.model.Citation;
import java.util.List;
import java.util.UUID;

/**
 * Where an answer is delivered while it is written, e.g. a server-sent event stream. Implementations throw
 * {@link VisitorGoneException} when the visitor is no longer listening, which stops the generation.
 */
public interface AnswerSink {

    void text(String delta);

    /**
     * @param pendingAction an action the answer proposed, for the visitor to confirm, or null
     */
    void completed(UUID messageId, AnswerOutcome outcome, List<Citation> citations, PendingActionView pendingAction);

    /**
     * For answers nobody is waiting for live, e.g. on WhatsApp, where the reply is sent once it is recorded.
     */
    AnswerSink NONE = new AnswerSink() {
        @Override
        public void text(String delta) {
        }

        @Override
        public void completed(UUID messageId, AnswerOutcome outcome, List<Citation> citations,
                PendingActionView pendingAction) {
        }
    };

    class VisitorGoneException extends RuntimeException {

        public VisitorGoneException(Throwable cause) {
            super("The visitor disconnected", cause);
        }
    }
}
