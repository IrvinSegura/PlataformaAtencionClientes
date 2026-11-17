package com.irvinsegura.supportchat.knowledge.application;

import com.irvinsegura.supportchat.knowledge.QuestionMeaning;
import com.irvinsegura.supportchat.knowledge.domain.port.EmbeddingModel;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Questions are embedded as queries, the same way the knowledge search embeds them, so comparing two questions
 * compares vectors of one kind and the search reuses the vector instead of asking the model again.
 */
@Service
class QuestionMeaningService implements QuestionMeaning {

    private static final Logger log = LoggerFactory.getLogger(QuestionMeaningService.class);

    private final QueryEmbeddings queries;
    private final EmbeddingModel embeddings;

    QuestionMeaningService(QueryEmbeddings queries, EmbeddingModel embeddings) {
        this.queries = queries;
        this.embeddings = embeddings;
    }

    @Override
    public Optional<float[]> of(String question) {
        if (question == null || question.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(queries.of(question.strip()).values());
        } catch (RuntimeException e) {
            // Whoever compares questions falls back to their words
            log.warn("Could not embed a question to compare it with others: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public double sameQuestionSimilarity() {
        return embeddings.sameQuestionSimilarity();
    }
}
