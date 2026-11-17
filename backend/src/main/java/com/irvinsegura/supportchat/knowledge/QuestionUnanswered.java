package com.irvinsegura.supportchat.knowledge;

import java.time.Instant;
import java.util.UUID;

/**
 * The assistant could not answer a visitor's question from the knowledge base: a gap worth documenting. The
 * conversations module publishes it; this module records it as a knowledge gap.
 */
public record QuestionUnanswered(UUID workspaceId, UUID conversationId, UUID messageId, String question,
        Instant askedAt) {
}
