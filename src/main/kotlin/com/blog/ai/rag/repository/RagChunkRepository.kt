package com.blog.ai.rag.repository

import com.blog.ai.jooq.tables.references.RAG_CHUNKS
import com.blog.ai.rag.model.RagChunkGranularity
import com.blog.ai.rag.model.RagChunkHit
import com.blog.ai.rag.model.RagChunkWrite
import com.blog.ai.rag.model.RagSearchQuery
import com.blog.ai.rag.model.RagSourceType
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class RagChunkRepository(
    private val dsl: DSLContext,
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
        dsl
            .deleteFrom(RAG_CHUNKS)
            .where(RAG_CHUNKS.SOURCE_TYPE.eq(sourceType.name))
            .and(RAG_CHUNKS.SOURCE_ID.eq(sourceId))
            .execute()
    }

    fun deleteAllBySourceType(sourceType: RagSourceType) {
        dsl.deleteFrom(RAG_CHUNKS).where(RAG_CHUNKS.SOURCE_TYPE.eq(sourceType.name)).execute()
    }

    private fun save(command: RagChunkWrite) {
        dsl
            .query(
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
            ).execute()
    }

    fun searchHybrid(query: RagSearchQuery): List<RagChunkHit> =
        dsl
            .resultQuery(
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
                query.queryVector,
                query.queryText,
                query.sourceType.name,
                query.granularity.name,
                query.candidatePoolSize,
                query.sourceType.name,
                query.granularity.name,
                query.candidatePoolSize,
                query.limit,
            ).fetch(::toHit)

    fun findDocumentVector(
        sourceType: RagSourceType,
        sourceId: Long,
    ): String? =
        dsl
            .select(DSL.field("embedding::text", String::class.java))
            .from(RAG_CHUNKS)
            .where(RAG_CHUNKS.SOURCE_TYPE.eq(sourceType.name))
            .and(RAG_CHUNKS.SOURCE_ID.eq(sourceId))
            .and(RAG_CHUNKS.GRANULARITY.eq(RagChunkGranularity.DOCUMENT.name))
            .limit(1)
            .fetchOne(0, String::class.java)

    private fun toHit(record: Record): RagChunkHit =
        RagChunkHit(
            sourceType = RagSourceType.valueOf(record.get("source_type", String::class.java)),
            sourceId = record.get("source_id", Long::class.java),
            granularity = RagChunkGranularity.valueOf(record.get("granularity", String::class.java)),
            chunkIndex = record.get("chunk_index", Int::class.java),
            title = record.get("title", String::class.java),
            url = record.get("url", String::class.java),
            company = record.get("company", String::class.java),
            content = record.get("content", String::class.java),
            similarity = record.get("similarity", Double::class.java),
            score = record.get("score", Double::class.java),
        )
}
