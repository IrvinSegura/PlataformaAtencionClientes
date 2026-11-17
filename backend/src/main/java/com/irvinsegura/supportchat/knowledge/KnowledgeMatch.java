package com.irvinsegura.supportchat.knowledge;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A passage found for a question.
 *
 * @param similarity   cosine similarity between question and passage, or {@code null} when only the keyword
 *                     retriever found it
 * @param keywordMatch whether the passage contains words of the question
 * @param score        fused rank score; only meaningful for ordering matches of the same search
 */
public record KnowledgeMatch(UUID chunkId, UUID documentId, String documentTitle, String content,
        @Nullable Double similarity, boolean keywordMatch, double score) {
}
