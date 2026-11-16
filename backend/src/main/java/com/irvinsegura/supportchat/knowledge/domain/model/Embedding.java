package com.irvinsegura.supportchat.knowledge.domain.model;

import java.util.Arrays;

/**
 * A vector of exactly {@link KnowledgeLimits#EMBEDDING_DIMENSIONS} finite values, as the database column requires.
 */
public record Embedding(float[] values) {

    public Embedding {
        if (values == null || values.length != KnowledgeLimits.EMBEDDING_DIMENSIONS) {
            throw new IllegalArgumentException("Embeddings must have %d dimensions"
                    .formatted(KnowledgeLimits.EMBEDDING_DIMENSIONS));
        }
        for (float value : values) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Embeddings must contain finite values");
            }
        }
        values = values.clone();
    }

    @Override
    public float[] values() {
        return values.clone();
    }

    /**
     * pgvector's text format, e.g. {@code [0.1,0.2]}.
     */
    public String toVectorLiteral() {
        StringBuilder literal = new StringBuilder(values.length * 10).append('[');
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                literal.append(',');
            }
            literal.append(values[i]);
        }
        return literal.append(']').toString();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Embedding embedding && Arrays.equals(values, embedding.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return "Embedding[" + values.length + " dimensions]";
    }
}
