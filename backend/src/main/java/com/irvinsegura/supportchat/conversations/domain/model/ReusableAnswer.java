package com.irvinsegura.supportchat.conversations.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * An answer the model wrote to a question asked without earlier context, kept to answer the same question again for
 * free while nothing it depended on changed.
 *
 * @param questionKey   the question as {@link QuestionKey} folds it
 * @param preference    the model choice of the channel it was written for
 * @param contextDigest a digest of the knowledge base, actions and assistant it was written with
 * @param model         the model that wrote it
 * @param tier          the tier of that model, or null when no tier applied
 */
public record ReusableAnswer(String questionKey, String preference, String contextDigest, String text,
        List<Citation> citations, String model, ModelTier tier, Instant createdAt) {

    public ReusableAnswer {
        Objects.requireNonNull(questionKey);
        Objects.requireNonNull(preference);
        Objects.requireNonNull(contextDigest);
        Objects.requireNonNull(text);
        Objects.requireNonNull(model);
        Objects.requireNonNull(createdAt);
        citations = List.copyOf(citations);
    }
}
