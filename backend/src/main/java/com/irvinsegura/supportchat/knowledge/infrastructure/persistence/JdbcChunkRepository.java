package com.irvinsegura.supportchat.knowledge.infrastructure.persistence;

import com.irvinsegura.supportchat.knowledge.domain.model.ChunkHit;
import com.irvinsegura.supportchat.knowledge.domain.model.Embedding;
import com.irvinsegura.supportchat.knowledge.domain.port.ChunkRepository;
import com.irvinsegura.supportchat.shared.domain.Ids;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Like every workspace table, passages are filtered by row-level security, never by a condition written here.
 */
@Repository
class JdbcChunkRepository implements ChunkRepository {

    // HNSW explores this many candidates per search; higher finds better neighbours at some latency cost
    private static final int MIN_EF_SEARCH = 64;

    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate batch;

    JdbcChunkRepository(JdbcClient jdbc, NamedParameterJdbcTemplate batch) {
        this.jdbc = jdbc;
        this.batch = batch;
    }

    @Override
    @Transactional
    public void replace(UUID documentId, UUID workspaceId, List<StoredChunk> chunks) {
        jdbc.sql("DELETE FROM document_chunks WHERE document_id = :documentId")
                .param("documentId", documentId)
                .update();
        SqlParameterSource[] rows = new SqlParameterSource[chunks.size()];
        for (int i = 0; i < chunks.size(); i++) {
            rows[i] = new MapSqlParameterSource()
                    .addValue("id", Ids.newId())
                    .addValue("workspaceId", workspaceId)
                    .addValue("documentId", documentId)
                    .addValue("index", i)
                    .addValue("content", chunks.get(i).content())
                    .addValue("embedding", chunks.get(i).embedding().toVectorLiteral());
        }
        batch.batchUpdate("""
                INSERT INTO document_chunks (id, workspace_id, document_id, chunk_index, content, embedding)
                VALUES (:id, :workspaceId, :documentId, :index, :content, CAST(:embedding AS vector))
                """, rows);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChunkHit> nearest(Embedding query, int limit) {
        // The HNSW index is shared by all workspaces and row-level security filters its results afterwards.
        // Iterative scans (pgvector 0.8) keep reading the index until enough rows of this workspace are found,
        // instead of returning too few when other workspaces own most of the nearest vectors. Relaxed order is
        // corrected by the outer ORDER BY
        jdbc.sql("SELECT set_config('hnsw.iterative_scan', 'relaxed_order', true)").query().singleValue();
        jdbc.sql("SELECT set_config('hnsw.ef_search', :efSearch, true)")
                .param("efSearch", String.valueOf(Math.max(MIN_EF_SEARCH, limit * 2)))
                .query().singleValue();
        return jdbc.sql("""
                        WITH nearest AS MATERIALIZED (
                            SELECT c.id, c.document_id, c.content,
                                   c.embedding <=> CAST(:query AS vector) AS distance
                            FROM document_chunks c
                            ORDER BY distance
                            LIMIT :limit
                        )
                        SELECT n.id, n.document_id, d.title, n.content, 1 - n.distance AS score
                        FROM nearest n
                        JOIN documents d ON d.id = n.document_id
                        ORDER BY n.distance, n.id
                        """)
                .param("query", query.toVectorLiteral())
                .param("limit", limit)
                .query(JdbcChunkRepository::map)
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChunkHit> matching(String tsQuery, int limit) {
        return jdbc.sql("""
                        SELECT c.id, c.document_id, d.title, c.content, ts_rank_cd(c.search_vector, q) AS score
                        FROM document_chunks c
                        JOIN documents d ON d.id = c.document_id
                        CROSS JOIN to_tsquery('simple', :query) AS q
                        WHERE c.search_vector @@ q
                        ORDER BY score DESC, c.id
                        LIMIT :limit
                        """)
                .param("query", tsQuery)
                .param("limit", limit)
                .query(JdbcChunkRepository::map)
                .list();
    }

    private static ChunkHit map(ResultSet rs, int row) throws SQLException {
        return new ChunkHit(
                rs.getObject("id", UUID.class),
                rs.getObject("document_id", UUID.class),
                rs.getString("title"),
                rs.getString("content"),
                rs.getDouble("score"));
    }
}
