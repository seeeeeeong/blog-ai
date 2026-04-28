package com.blog.ai.rag.infrastructure

import com.blog.ai.rag.domain.RagChunkGranularity
import com.blog.ai.rag.domain.RagChunkHit
import com.blog.ai.rag.domain.RagChunkWrite
import com.blog.ai.rag.domain.RagSearchQuery
import com.blog.ai.rag.domain.RagSourceType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class RagChunkRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun replaceSource(
        sourceType: RagSourceType,
        sourceId: Long,
        chunks: List<RagChunkWrite>,
    ) {
        deleteSource(sourceType, sourceId)
        chunks.forEach(::save)
    }

    fun deleteSource(
        sourceType: RagSourceType,
        sourceId: Long,
    ) {
        jdbcTemplate.update(
            "DELETE FROM rag_chunks WHERE source_type = ? AND source_id = ?",
            sourceType.name,
            sourceId,
        )
    }

    fun deleteAllBySourceType(sourceType: RagSourceType) {
        jdbcTemplate.update("DELETE FROM rag_chunks WHERE source_type = ?", sourceType.name)
    }

    private fun save(command: RagChunkWrite) {
        jdbcTemplate.update(
            """
            INSERT INTO rag_chunks (
                source_type, source_id, granularity, chunk_index,
                title, url, company, content, embedding, search_vector, metadata
            )
            VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS vector),
                setweight(to_tsvector('simple', korean_bigrams(?)), 'A') ||
                    setweight(to_tsvector('simple', korean_bigrams(?)), 'B'),
                CAST(? AS jsonb)
            )
            """.trimIndent(),
            command.sourceType.name,
            command.sourceId,
            command.granularity.name,
            command.chunkIndex,
            command.title,
            command.url,
            command.company,
            command.content,
            command.embedding,
            command.title,
            command.content,
            command.metadataJson,
        )
    }

    fun searchHybrid(query: RagSearchQuery): List<RagChunkHit> =
        jdbcTemplate.query(
            """
            WITH q AS (
                SELECT CAST(? AS vector) AS v,
                       korean_bigram_tsquery(?) AS ts
            ),
            vec AS (
                SELECT r.id,
                       ROW_NUMBER() OVER (ORDER BY r.embedding <=> (SELECT v FROM q)) AS rnk
                FROM rag_chunks r
                WHERE r.source_type = ?
                  AND r.granularity = ?
                ORDER BY r.embedding <=> (SELECT v FROM q)
                LIMIT ?
            ),
            bm25 AS (
                SELECT r.id,
                       ROW_NUMBER() OVER (
                           ORDER BY ts_rank_cd(r.search_vector, (SELECT ts FROM q)) DESC
                       ) AS rnk
                FROM rag_chunks r
                WHERE r.source_type = ?
                  AND r.granularity = ?
                  AND r.search_vector @@ (SELECT ts FROM q)
                ORDER BY ts_rank_cd(r.search_vector, (SELECT ts FROM q)) DESC
                LIMIT ?
            ),
            fused AS (
                SELECT COALESCE(vec.id, bm25.id) AS id,
                       COALESCE(1.0 / (60 + vec.rnk), 0) +
                       COALESCE(1.0 / (60 + bm25.rnk), 0) AS score
                FROM vec
                FULL OUTER JOIN bm25 ON vec.id = bm25.id
            )
            SELECT
                r.source_type,
                r.source_id,
                r.granularity,
                r.chunk_index,
                r.title,
                r.url,
                r.company,
                r.content,
                1 - (r.embedding <=> (SELECT v FROM q)) AS similarity,
                f.score
            FROM fused f
            JOIN rag_chunks r ON r.id = f.id
            ORDER BY f.score DESC, r.embedding <=> (SELECT v FROM q)
            LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                RagChunkHit(
                    sourceType = RagSourceType.valueOf(rs.getString("source_type")),
                    sourceId = rs.getLong("source_id"),
                    granularity = RagChunkGranularity.valueOf(rs.getString("granularity")),
                    chunkIndex = rs.getInt("chunk_index"),
                    title = rs.getString("title"),
                    url = rs.getString("url"),
                    company = rs.getString("company"),
                    content = rs.getString("content"),
                    similarity = rs.getDouble("similarity"),
                    score = rs.getDouble("score"),
                )
            },
            query.queryVector,
            query.queryText,
            query.sourceType.name,
            query.granularity.name,
            query.candidatePoolSize,
            query.sourceType.name,
            query.granularity.name,
            query.candidatePoolSize,
            query.limit,
        )

    fun findDocumentVector(
        sourceType: RagSourceType,
        sourceId: Long,
    ): String? {
        val rows =
            jdbcTemplate.query(
                """
                SELECT embedding::text AS embedding
                FROM rag_chunks
                WHERE source_type = ? AND source_id = ? AND granularity = 'DOCUMENT'
                LIMIT 1
                """.trimIndent(),
                { rs, _ -> rs.getString("embedding") },
                sourceType.name,
                sourceId,
            )
        return rows.firstOrNull()
    }
}
