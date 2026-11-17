package com.irvinsegura.supportchat.conversations.domain.model;

import java.util.UUID;

/**
 * A passage offered to the model as evidence, numbered as the model must cite it.
 *
 * @param similarity cosine similarity to the question, or null when only the keyword retriever found it
 */
public record Source(int number, UUID chunkId, UUID documentId, String documentTitle, String content,
        Double similarity, boolean keywordMatch) {

    private static final int EXCERPT_LENGTH = 200;

    public Citation toCitation() {
        String excerpt = content.length() <= EXCERPT_LENGTH ? content : content.substring(0, EXCERPT_LENGTH) + "…";
        return new Citation(number, documentId, documentTitle, chunkId, excerpt);
    }
}
