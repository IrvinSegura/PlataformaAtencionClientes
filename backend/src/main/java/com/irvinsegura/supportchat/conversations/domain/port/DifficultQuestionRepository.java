package com.irvinsegura.supportchat.conversations.domain.port;

import com.irvinsegura.supportchat.conversations.domain.model.DifficultQuestion;
import com.irvinsegura.supportchat.conversations.domain.model.DifficultQuestion.Reason;
import com.irvinsegura.supportchat.conversations.domain.model.DifficultQuestion.Status;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Questions the assistant struggled with, in the workspace bound to the current transaction.
 * <p>
 * An open question is <em>active</em> while it was found difficult, or opened, at or after the given instant: one
 * quiet for longer no longer goes to the capable model, and is closed by {@link #closeQuiet}.
 */
public interface DifficultQuestionRepository {

    /**
     * Records a question found difficult for the first time, or once more when its key is already known (two
     * customers recording it at the same time).
     *
     * @param words   the question's {@link com.irvinsegura.supportchat.conversations.domain.model.QuestionWords}
     * @param meaning its embedding, or null when the embedding model could not be reached
     */
    Recorded record(UUID workspaceId, UUID newId, String questionKey, String question, Set<String> words,
            float[] meaning, Reason reason, UUID conversationId, String lastAnswer, Instant now);

    /**
     * Records that a known question was found difficult once more, perhaps in other words. It opens again if the team
     * had taught its answer (what they taught did not help either) or it had closed by itself, and its run of good
     * answers starts over. A question the team dismissed stays dismissed.
     *
     * @param meaning kept only when the question had none yet, so the first wording stays the one others are compared
     *                with
     * @return empty when no such question exists any more
     */
    Optional<Recorded> recordAgain(UUID id, String question, float[] meaning, Reason reason, UUID conversationId,
            String lastAnswer, Instant now);

    Optional<UUID> findByKey(String questionKey);

    /**
     * Questions that share at least one word with these, at most {@code limit}, most recent first.
     *
     * @param activeSince only active open questions when given; every question, whatever its status, when null
     */
    List<WordsCandidate> sharingWords(Set<String> words, Instant activeSince, int limit);

    /**
     * The question closest in meaning, among those with an embedding.
     *
     * @param activeSince only active open questions when given; every question, whatever its status, when null
     */
    Optional<MeaningCandidate> closestInMeaning(float[] meaning, Instant activeSince);

    boolean hasActive(Instant activeSince);

    /**
     * One more answer of the capable model to an open question; at {@code toClose} in a row it closes by itself.
     *
     * @return whether it closed
     */
    boolean answeredWell(UUID id, int toClose, Instant now);

    /**
     * Closes the open questions that are no longer active.
     *
     * @return how many closed
     */
    int closeQuiet(Instant activeSince, Instant now);

    /**
     * Notes that the team is being told the question keeps coming back, if it is open, was found difficult at least
     * {@code minOccurrences} times and nobody was told before.
     *
     * @return whether the team should be told now; false when another caller already noted it
     */
    boolean markAlerted(UUID id, long minOccurrences, Instant now);

    Optional<DifficultQuestion> findById(UUID id);

    Page page(Status status, int page, int size);

    /**
     * Sets the team's decision; a question opened again starts its run of good answers over.
     */
    boolean changeStatus(UUID id, Status status, Instant now);

    /**
     * How many questions were found difficult for the first time in the period.
     */
    long countFirstSeen(Instant from, Instant to);

    /**
     * A question as it stands right after being recorded.
     *
     * @param alerted whether the team was already told it keeps coming back
     */
    record Recorded(UUID id, Status status, long occurrences, boolean alerted) {
    }

    record WordsCandidate(UUID id, Set<String> words) {
    }

    record MeaningCandidate(UUID id, double similarity) {
    }

    record Page(List<DifficultQuestion> items, long total) {
    }
}
