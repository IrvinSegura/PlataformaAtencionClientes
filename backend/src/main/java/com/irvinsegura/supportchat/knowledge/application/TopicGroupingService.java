package com.irvinsegura.supportchat.knowledge.application;

import com.irvinsegura.supportchat.knowledge.TopicGrouping;
import com.irvinsegura.supportchat.knowledge.domain.model.Embedding;
import com.irvinsegura.supportchat.knowledge.domain.model.TopicClusters;
import com.irvinsegura.supportchat.knowledge.domain.port.EmbeddingModel;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
class TopicGroupingService implements TopicGrouping {

    // Same size as ingestion: well under what providers accept, so each call stays short
    private static final int BATCH_SIZE = 64;

    private final EmbeddingModel embeddings;

    TopicGroupingService(EmbeddingModel embeddings) {
        this.embeddings = embeddings;
    }

    @Override
    public List<List<Integer>> group(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += BATCH_SIZE) {
            // Texts compared with texts of the same kind: the passage side of asymmetric models fits both
            embeddings.embedPassages(texts.subList(from, Math.min(from + BATCH_SIZE, texts.size()))).stream()
                    .map(Embedding::values)
                    .forEach(vectors::add);
        }
        return TopicClusters.group(vectors, embeddings.sameTopicSimilarity());
    }
}
