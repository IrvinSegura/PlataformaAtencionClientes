package com.irvinsegura.supportchat.conversations.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides which retrieved passages are relevant enough to show the model. Sending weak passages invites the model
 * to stretch them into an answer; sending none lets the platform say "I don't know" without paying for a model call.
 * <p>
 * A passage qualifies when it is semantically close to the question, or when it contains the question's words and
 * is at least loosely related, which catches exact terms (codes, names) that embeddings rank low.
 *
 * @param minSimilarity cosine similarity a passage needs on meaning alone
 * @param maxSources    most passages sent to the model
 */
public record SourceSelection(double minSimilarity, int maxSources) {

    public SourceSelection {
        if (minSimilarity < -1 || minSimilarity > 1 || maxSources < 1) {
            throw new IllegalArgumentException("Invalid source selection settings");
        }
    }

    /**
     * Qualifying candidates in their original (best first) order, renumbered from 1.
     */
    public List<Source> select(List<Source> rankedCandidates) {
        List<Source> selected = new ArrayList<>();
        for (Source candidate : rankedCandidates) {
            if (selected.size() == maxSources) {
                break;
            }
            if (qualifies(candidate)) {
                selected.add(new Source(selected.size() + 1, candidate.chunkId(), candidate.documentId(),
                        candidate.documentTitle(), candidate.content(), candidate.similarity(),
                        candidate.keywordMatch()));
            }
        }
        return selected;
    }

    private boolean qualifies(Source candidate) {
        Double similarity = candidate.similarity();
        if (similarity != null && similarity >= minSimilarity) {
            return true;
        }
        return candidate.keywordMatch() && (similarity == null || similarity >= minSimilarity / 2);
    }
}
