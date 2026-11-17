package com.irvinsegura.supportchat.conversations.domain.port;

import com.irvinsegura.supportchat.conversations.domain.model.ReusableAnswer;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Answers kept to answer the same question again, in the workspace bound to the current transaction.
 */
public interface ReusableAnswerRepository {

    /**
     * The answer to that question written with that context no earlier than {@code notBefore}.
     */
    Optional<ReusableAnswer> find(String questionKey, String preference, String contextDigest, Instant notBefore);

    /**
     * Keeps the answer, replacing the one kept for the same question and context.
     */
    void save(UUID workspaceId, ReusableAnswer answer);

    /**
     * Counts one more time the answer was given again.
     */
    void markReused(String questionKey, String preference, String contextDigest, Instant now);

    /**
     * Forgets every kept answer with that text, e.g. after a customer said it did not help.
     *
     * @return how many were forgotten
     */
    int forget(String text);

    /**
     * Forgets the answers written before that instant, which can no longer be given.
     */
    int deleteWrittenBefore(Instant instant);
}
