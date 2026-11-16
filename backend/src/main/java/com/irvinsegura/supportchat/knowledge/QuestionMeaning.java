package com.irvinsegura.supportchat.knowledge;

import java.util.Optional;

/**
 * What a question means, as a vector of the knowledge base's embedding model, to tell when two questions are the
 * same one asked in other words: "¿Cuánto tarda el envío?" and "¿En cuántos días llega mi paquete?" share no word
 * that matters, yet ask the same.
 */
public interface QuestionMeaning {

    /**
     * The question's vector, or empty when the embedding model cannot be reached right now. The same question asked
     * again within a few minutes, as the knowledge search does, is not sent to the model twice.
     */
    Optional<float[]> of(String question);

    /**
     * Cosine similarity from which two questions are usually the same one, for the embedding model in use.
     */
    double sameQuestionSimilarity();
}
