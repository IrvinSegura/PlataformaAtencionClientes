package com.irvinsegura.supportchat.knowledge.domain.port;

import com.irvinsegura.supportchat.knowledge.domain.model.GapStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Knowledge gaps of the workspace bound to the current transaction.
 */
public interface KnowledgeGapRepository {

    /**
     * Counts one unanswered question under its gap, creating the gap the first time. A resolved gap asked again is
     * reopened. Recording the same message twice changes nothing.
     */
    void recordOccurrence(UUID workspaceId, UUID newGapId, String questionKey, String question, UUID conversationId,
            UUID messageId, Instant askedAt);

    GapPage page(GapStatus status, int page, int size);

    /**
     * @return whether the gap exists
     */
    boolean changeStatus(UUID gapId, GapStatus status, Instant now);

    /**
     * The latest wording of the gap's question.
     */
    Optional<String> question(UUID gapId);

    record GapPage(List<GapSummary> gaps, long total) {
    }

    /**
     * @param lastConversationId where it was asked most recently, for the operator to read the context
     */
    record GapSummary(UUID id, String question, GapStatus status, long occurrences, Instant firstAskedAt,
            Instant lastAskedAt, UUID lastConversationId) {
    }
}
