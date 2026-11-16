package com.irvinsegura.supportchat.knowledge.domain.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reciprocal rank fusion (Cormack et al., 2009): each ranking adds {@code 1 / (k + rank)} to every item it contains.
 * It combines retrievers whose raw scores are not comparable (cosine similarity and text rank) using positions only,
 * and rewards items that several retrievers agree on.
 */
public final class RankFusion {

    /**
     * The constant from the original paper; it dampens the weight of the very top positions.
     */
    public static final int DEFAULT_K = 60;

    private RankFusion() {
    }

    /**
     * Fused ranking, best first. Ties keep the order in which items were first seen, so results are deterministic.
     */
    public static List<FusedRank> fuse(List<List<UUID>> rankings, int k) {
        Map<UUID, Double> scores = new LinkedHashMap<>();
        for (List<UUID> ranking : rankings) {
            for (int position = 0; position < ranking.size(); position++) {
                scores.merge(ranking.get(position), 1.0 / (k + position + 1), Double::sum);
            }
        }
        List<FusedRank> fused = new ArrayList<>(scores.size());
        scores.forEach((id, score) -> fused.add(new FusedRank(id, score)));
        // Stable sort: equal scores keep their first-seen order
        fused.sort(Comparator.comparingDouble(FusedRank::score).reversed());
        return fused;
    }

    public record FusedRank(UUID id, double score) {
    }
}
