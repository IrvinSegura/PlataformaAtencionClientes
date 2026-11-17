package com.irvinsegura.supportchat.knowledge.domain.port;

import com.irvinsegura.supportchat.knowledge.domain.model.ChunkHit;
import com.irvinsegura.supportchat.knowledge.domain.model.Embedding;
import java.util.List;
import java.util.UUID;

/**
 * Searchable passages of the workspace bound to the current transaction.
 */
public interface ChunkRepository {

    /**
     * Swaps all passages of a document at once, so searches see either the old set or the new one, never a mix.
     */
    void replace(UUID documentId, UUID workspaceId, List<StoredChunk> chunks);

    /**
     * Nearest passages by cosine similarity.
     */
    List<ChunkHit> nearest(Embedding query, int limit);

    /**
     * Passages matching a {@code to_tsquery} expression, best text rank first.
     */
    List<ChunkHit> matching(String tsQuery, int limit);

    record StoredChunk(String content, Embedding embedding) {
    }
}
