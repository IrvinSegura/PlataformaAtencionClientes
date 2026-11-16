package com.irvinsegura.supportchat.knowledge.domain.model;

import java.util.UUID;

/**
 * A passage returned by one retriever.
 *
 * @param score the retriever's own score: cosine similarity for vectors, text rank for keywords
 */
public record ChunkHit(UUID chunkId, UUID documentId, String documentTitle, String content, double score) {
}
