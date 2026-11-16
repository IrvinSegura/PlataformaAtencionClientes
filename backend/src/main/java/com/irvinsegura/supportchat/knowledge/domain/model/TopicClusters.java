package com.irvinsegura.supportchat.knowledge.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Leader clustering: each vector joins the group whose first member it is most similar to, if at least
 * {@code threshold}, or starts a new group. Comparing with the leader only, not every member, keeps a chain of
 * slightly related texts from merging topics that have nothing in common.
 */
public final class TopicClusters {

    private TopicClusters() {
    }

    /**
     * @param vectors unit vectors, as the embedding models produce, so the dot product is the cosine similarity
     */
    public static List<List<Integer>> group(List<float[]> vectors, double threshold) {
        List<List<Integer>> groups = new ArrayList<>();
        for (int index = 0; index < vectors.size(); index++) {
            List<Integer> best = null;
            double bestSimilarity = threshold;
            for (List<Integer> group : groups) {
                double similarity = dot(vectors.get(group.getFirst()), vectors.get(index));
                if (similarity >= bestSimilarity) {
                    best = group;
                    bestSimilarity = similarity;
                }
            }
            if (best == null) {
                best = new ArrayList<>();
                groups.add(best);
            }
            best.add(index);
        }
        return groups.stream().map(List::copyOf).toList();
    }

    static double dot(float[] left, float[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException("Vectors of different dimensions");
        }
        double sum = 0;
        for (int i = 0; i < left.length; i++) {
            sum += (double) left[i] * right[i];
        }
        return sum;
    }
}
