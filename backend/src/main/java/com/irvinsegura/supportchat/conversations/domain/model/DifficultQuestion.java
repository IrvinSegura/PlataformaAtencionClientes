package com.irvinsegura.supportchat.conversations.domain.model;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A question the assistant struggled with: the fast model could not answer it, or the customer showed the answer did
 * not help. While it is open, the same question goes straight to the capable model; once the team teaches its answer,
 * the knowledge base holds it and any model can answer it. Nobody teaching it must not cost the capable model
 * forever, so it also closes by itself (see {@link Resolution}).
 *
 * @param question    the latest wording customers used
 * @param reason      the latest sign that it was difficult
 * @param lastAnswer  what the assistant answered the last time, so the team sees what went wrong
 * @param occurrences how many times it was found difficult
 * @param resolution  why it closed by itself; null unless {@link Status#RESOLVED}
 * @param alertedAt   when the team was told by WhatsApp that it keeps coming back, or null
 */
public record DifficultQuestion(UUID id, String question, Reason reason, Status status, @Nullable String lastAnswer,
        @Nullable UUID conversationId, long occurrences, Instant firstSeenAt, Instant lastSeenAt,
        @Nullable Resolution resolution, @Nullable Instant alertedAt) {

    public enum Reason {
        // The fast model found no answer and the capable one did
        ESCALATED,
        // The customer said the answer did not help
        UNHELPFUL,
        // The customer asked the same again right after the answer
        ASKED_AGAIN,
        // The customer wrote that the answer was wrong or did not help ("no entendí")
        UNSATISFIED
    }

    public enum Status {
        OPEN,
        // The team wrote the right answer into the knowledge base
        TAUGHT,
        // The team decided it needs nothing
        DISMISSED,
        // Closed by itself; found difficult again, it opens again
        RESOLVED
    }

    public enum Resolution {
        // The capable model answered it several times in a row and nobody found the answers lacking
        ANSWERED_WELL,
        // Nobody found it difficult again for a while
        QUIET
    }
}
