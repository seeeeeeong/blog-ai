package com.blog.ai.storage.article

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ArticleRepository : JpaRepository<ArticleEntity, Long> {
    fun existsByUrlHash(urlHash: String): Boolean

    @Query(
        value = "SELECT * FROM articles WHERE embedding IS NULL AND embed_error IS NULL ORDER BY id LIMIT :limit",
        nativeQuery = true,
    )
    fun findUnembedded(limit: Int): List<ArticleEntity>

    @Modifying
    @Query(
        value = """
            UPDATE articles
            SET embedding = CAST(:embedding AS vector),
                search_vector =
                    setweight(to_tsvector('simple', korean_bigrams(:title)), 'A') ||
                    setweight(to_tsvector('simple', korean_bigrams(:content)), 'B'),
                embed_error = NULL
            WHERE id = :id
        """,
        nativeQuery = true,
    )
    fun updateEmbedding(
        id: Long,
        embedding: String,
        title: String,
        content: String,
    )

    @Modifying
    @Query(
        value = "UPDATE articles SET embed_error = :error, embed_retry_count = embed_retry_count + 1 WHERE id = :id",
        nativeQuery = true,
    )
    fun updateEmbedError(
        id: Long,
        error: String,
    )

    @Modifying
    @Query(
        value =
            "UPDATE articles SET embed_error = NULL " +
                "WHERE embed_error IS NOT NULL AND embed_retry_count <= :maxRetries",
        nativeQuery = true,
    )
    fun clearRetriableEmbedErrors(maxRetries: Int): Int

    @Query(
        value = """
            SELECT a.id, a.title, a.url, b.company,
                   1 - (a.embedding <=> CAST(:vector AS vector)) AS score
            FROM articles a
            JOIN blogs b ON b.id = a.blog_id
            WHERE a.embedding IS NOT NULL
            ORDER BY a.embedding <=> CAST(:vector AS vector)
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findSimilarByVector(
        vector: String,
        limit: Int,
    ): List<Array<Any>>

    @Query(
        value = """
            SELECT a.id, a.title, a.url, b.company,
                   ts_rank_cd(
                       a.search_vector,
                       korean_bigram_tsquery(:queryText)
                   ) AS score
            FROM articles a
            JOIN blogs b ON b.id = a.blog_id
            WHERE a.search_vector @@ korean_bigram_tsquery(:queryText)
            ORDER BY score DESC
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findSimilarByBm25(
        queryText: String,
        limit: Int,
    ): List<Array<Any>>

    @Query(
        value = """
            WITH q AS (
                SELECT CAST(:vector AS vector) AS v,
                       korean_bigram_tsquery(:queryText) AS ts
            ),
            vec AS (
                SELECT a.id,
                       ROW_NUMBER() OVER (ORDER BY a.embedding <=> (SELECT v FROM q)) AS rnk
                FROM articles a
                WHERE a.embedding IS NOT NULL
                ORDER BY a.embedding <=> (SELECT v FROM q)
                LIMIT :candidatePoolSize
            ),
            bm25 AS (
                SELECT a.id,
                       ROW_NUMBER() OVER (
                           ORDER BY ts_rank_cd(a.search_vector, (SELECT ts FROM q)) DESC
                       ) AS rnk
                FROM articles a
                WHERE a.search_vector @@ (SELECT ts FROM q)
                ORDER BY ts_rank_cd(a.search_vector, (SELECT ts FROM q)) DESC
                LIMIT :candidatePoolSize
            ),
            fused AS (
                SELECT COALESCE(vec.id, bm25.id) AS id,
                       COALESCE(1.0 / (60 + vec.rnk), 0) +
                       COALESCE(1.0 / (60 + bm25.rnk), 0) AS score
                FROM vec
                FULL OUTER JOIN bm25 ON vec.id = bm25.id
            )
            SELECT a.id, a.title, a.url, b.company, f.score
            FROM fused f
            JOIN articles a ON a.id = f.id
            JOIN blogs b ON b.id = a.blog_id
            ORDER BY f.score DESC
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findSimilarHybrid(
        vector: String,
        queryText: String,
        candidatePoolSize: Int,
        limit: Int,
    ): List<Array<Any>>

    @Query(
        value = """
            WITH q AS (
                SELECT CAST(:vector AS vector) AS v,
                       korean_bigram_tsquery(:queryText) AS ts
            ),
            vec AS (
                SELECT a.id,
                       ROW_NUMBER() OVER (ORDER BY a.embedding <=> (SELECT v FROM q)) AS rnk
                FROM articles a
                WHERE a.embedding IS NOT NULL
                ORDER BY a.embedding <=> (SELECT v FROM q)
                LIMIT :candidatePoolSize
            ),
            bm25 AS (
                SELECT a.id,
                       ROW_NUMBER() OVER (
                           ORDER BY ts_rank_cd(a.search_vector, (SELECT ts FROM q)) DESC
                       ) AS rnk
                FROM articles a
                WHERE a.search_vector @@ (SELECT ts FROM q)
                ORDER BY ts_rank_cd(a.search_vector, (SELECT ts FROM q)) DESC
                LIMIT :candidatePoolSize
            ),
            fused AS (
                SELECT COALESCE(vec.id, bm25.id) AS id,
                       COALESCE(1.0 / (60 + vec.rnk), 0) +
                       COALESCE(1.0 / (60 + bm25.rnk), 0) AS score
                FROM vec
                FULL OUTER JOIN bm25 ON vec.id = bm25.id
            )
            SELECT a.id, a.title, a.url, b.company, a.content, f.score
            FROM fused f
            JOIN articles a ON a.id = f.id
            JOIN blogs b ON b.id = a.blog_id
            ORDER BY f.score DESC
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findHybridForChat(
        vector: String,
        queryText: String,
        candidatePoolSize: Int,
        limit: Int,
    ): List<Array<Any>>

    @Query(
        value = """
            SELECT a.id, a.title, a.url, a.url_hash, b.company,
                   a.embedding IS NOT NULL AS embedded, a.embed_error, a.crawled_at
            FROM articles a
            JOIN blogs b ON b.id = a.blog_id
            ORDER BY a.crawled_at DESC
            LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true,
    )
    fun findArticlesForAdmin(
        limit: Int,
        offset: Int,
    ): List<Array<Any>>

    @Query("SELECT COUNT(*) FROM articles WHERE embedding IS NULL AND embed_error IS NULL", nativeQuery = true)
    fun countUnembedded(): Long

    @Query(
        value = "SELECT * FROM articles WHERE content IS NULL ORDER BY id LIMIT :limit",
        nativeQuery = true,
    )
    fun findWithoutContent(limit: Int): List<ArticleEntity>

    @Modifying
    @Query(
        value = "UPDATE articles SET content = :content WHERE id = :id",
        nativeQuery = true,
    )
    fun updateContent(
        id: Long,
        content: String,
    )

    @Modifying
    @Query(
        value = """
            UPDATE articles
            SET embedding = NULL, search_vector = NULL,
                embed_error = NULL, embed_retry_count = 0
            WHERE id = :id
        """,
        nativeQuery = true,
    )
    fun resetEmbeddingForArticle(id: Long)
}
