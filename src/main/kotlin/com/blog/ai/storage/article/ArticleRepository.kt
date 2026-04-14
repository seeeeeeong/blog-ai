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
                search_vector = to_tsvector('simple', :searchText),
                embed_error = NULL
            WHERE id = :id
        """,
        nativeQuery = true,
    )
    fun updateEmbedding(id: Long, embedding: String, searchText: String)

    @Modifying
    @Query("UPDATE articles SET embed_error = :error WHERE id = :id", nativeQuery = true)
    fun updateEmbedError(id: Long, error: String)

    @Modifying
    @Query("UPDATE articles SET embed_error = NULL WHERE embed_error IS NOT NULL", nativeQuery = true)
    fun clearAllEmbedErrors(): Int

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
    fun findSimilarByVector(vector: String, limit: Int): List<Array<Any>>

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
    fun findArticlesForAdmin(limit: Int, offset: Int): List<Array<Any>>

    @Query("SELECT COUNT(*) FROM articles WHERE embedding IS NULL AND embed_error IS NULL", nativeQuery = true)
    fun countUnembedded(): Long
}
