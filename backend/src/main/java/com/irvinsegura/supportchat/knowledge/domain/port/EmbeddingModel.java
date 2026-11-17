package com.irvinsegura.supportchat.knowledge.domain.port;

import com.irvinsegura.supportchat.knowledge.domain.model.Embedding;
import java.util.List;

/**
 * Turns text into vectors whose cosine similarity reflects closeness in meaning. Passages and questions are embedded
 * differently by some models (asymmetric retrieval), hence the two methods.
 *
 * @see com.irvinsegura.supportchat.knowledge.domain.model.EmbeddingUnavailableException thrown when the provider is
 * temporarily unreachable
 */
public interface EmbeddingModel {

    /**
     * One embedding per text, in the same order.
     */
    List<Embedding> embedPassages(List<String> passages);

    Embedding embedQuery(String query);

    /**
     * Cosine similarity from which a passage is usually about what the question asks. Each model spreads its scores
     * differently, so the value belongs to the model.
     */
    double relatedSimilarity();

    /**
     * Cosine similarity from which two short texts, such as two questions, are usually about the same topic.
     */
    double sameTopicSimilarity();

    /**
     * Cosine similarity from which two questions are usually the same one asked in other words, stricter than
     * {@link #sameTopicSimilarity()}: "¿Cuánto tarda el envío?" and "¿En cuántos días llega mi paquete?" are the same
     * question, while "envío a Monterrey" and "envío a Guadalajara" are only the same topic.
     */
    double sameQuestionSimilarity();
}
