package com.irvinsegura.supportchat.knowledge.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.irvinsegura.supportchat.knowledge.domain.model.Embedding;
import com.irvinsegura.supportchat.knowledge.domain.port.EmbeddingModel;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * The query embeddings of the last questions asked. Answering one question embeds it more than once (to tell whether
 * it was found difficult before, then to search the knowledge base); this sends it to the model once.
 */
@Component
class QueryEmbeddings {

    // A question is embedded again within seconds of being asked; minutes later it is only memory
    private static final Duration TIME_TO_LIVE = Duration.ofMinutes(2);
    private static final int MAX_QUESTIONS = 1_000;

    private final EmbeddingModel embeddings;
    // The vector depends only on the text, so one workspace's entry can never reveal another's question
    private final Cache<String, Embedding> recent = Caffeine.newBuilder()
            .maximumSize(MAX_QUESTIONS)
            .expireAfterWrite(TIME_TO_LIVE)
            .build();

    QueryEmbeddings(EmbeddingModel embeddings) {
        this.embeddings = embeddings;
    }

    /**
     * @throws com.irvinsegura.supportchat.knowledge.domain.model.EmbeddingUnavailableException when the model
     *                                                                                      cannot be reached
     */
    Embedding of(String question) {
        return recent.get(question, embeddings::embedQuery);
    }
}
