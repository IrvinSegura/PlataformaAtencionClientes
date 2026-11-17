package com.irvinsegura.supportchat.conversations.domain.port;

import com.irvinsegura.supportchat.conversations.domain.model.ConversationRating;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Feedback from customers in the workspace bound to the current transaction.
 */
public interface FeedbackRepository {

    /**
     * Records or replaces the verdict on one answer.
     */
    void saveAnswerFeedback(UUID workspaceId, UUID conversationId, UUID messageId, boolean helpful, Instant givenAt);

    /**
     * Whether each answer of the conversation that got a verdict helped, by message id.
     */
    Map<UUID, Boolean> answerFeedback(UUID conversationId);

    /**
     * Records or replaces the rating of the conversation.
     */
    void saveRating(UUID workspaceId, ConversationRating rating);

    Optional<ConversationRating> rating(UUID conversationId);
}
