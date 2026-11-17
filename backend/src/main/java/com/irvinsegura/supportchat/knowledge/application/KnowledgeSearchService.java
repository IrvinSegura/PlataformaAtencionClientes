package com.irvinsegura.supportchat.knowledge.application;

import com.irvinsegura.supportchat.knowledge.KnowledgeMatch;
import com.irvinsegura.supportchat.knowledge.KnowledgeSearch;
import com.irvinsegura.supportchat.knowledge.domain.model.ChunkHit;
import com.irvinsegura.supportchat.knowledge.domain.model.KeywordQuery;
import com.irvinsegura.supportchat.knowledge.domain.model.RankFusion;
import com.irvinsegura.supportchat.knowledge.domain.model.RankFusion.FusedRank;
import com.irvinsegura.supportchat.knowledge.domain.port.ChunkRepository;
import com.irvinsegura.supportchat.knowledge.domain.port.DocumentRepository;
import com.irvinsegura.supportchat.knowledge.domain.port.EmbeddingModel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Hybrid retrieval. The vector retriever finds passages that mean the same as the question even with other words;
 * the keyword retriever finds exact terms (codes, names, prices) that embeddings blur. Reciprocal rank fusion merges
 * both lists without having to compare their incompatible scores.
 */
@Service
class KnowledgeSearchService implements KnowledgeSearch {

    private final EmbeddingModel embeddings;
    private final QueryEmbeddings queries;
    private final ChunkRepository chunks;
    private final DocumentRepository documents;
    private final int candidatesPerRetriever;

    KnowledgeSearchService(EmbeddingModel embeddings, QueryEmbeddings queries, ChunkRepository chunks,
            DocumentRepository documents,
            @Value("${supportchat.knowledge.search.candidates-per-retriever}") int candidatesPerRetriever) {
        this.embeddings = embeddings;
        this.queries = queries;
        this.chunks = chunks;
        this.documents = documents;
        this.candidatesPerRetriever = candidatesPerRetriever;
    }

    @Override
    public double relatedSimilarity() {
        return embeddings.relatedSimilarity();
    }

    @Override
    public String revision() {
        return documents.revision();
    }

    @Override
    public List<KnowledgeMatch> search(String question, int limit) {
        if (question == null || question.isBlank() || limit <= 0) {
            return List.of();
        }
        int candidates = Math.max(limit, candidatesPerRetriever);
        List<ChunkHit> byMeaning = chunks.nearest(queries.of(question.strip()), candidates);
        List<ChunkHit> byKeyword = KeywordQuery.from(question)
                .map(query -> chunks.matching(query, candidates))
                .orElse(List.of());

        Map<UUID, ChunkHit> hits = new HashMap<>();
        Map<UUID, Double> similarities = new HashMap<>();
        byMeaning.forEach(hit -> {
            hits.put(hit.chunkId(), hit);
            similarities.put(hit.chunkId(), hit.score());
        });
        byKeyword.forEach(hit -> hits.putIfAbsent(hit.chunkId(), hit));
        List<UUID> keywordIds = byKeyword.stream().map(ChunkHit::chunkId).toList();
        Set<UUID> keywordMatches = new HashSet<>(keywordIds);

        List<FusedRank> fused = RankFusion.fuse(
                List.of(byMeaning.stream().map(ChunkHit::chunkId).toList(), keywordIds), RankFusion.DEFAULT_K);
        return fused.stream()
                .limit(limit)
                .map(rank -> {
                    ChunkHit hit = hits.get(rank.id());
                    return new KnowledgeMatch(hit.chunkId(), hit.documentId(), hit.documentTitle(), hit.content(),
                            similarities.get(rank.id()), keywordMatches.contains(rank.id()), rank.score());
                })
                .toList();
    }
}
