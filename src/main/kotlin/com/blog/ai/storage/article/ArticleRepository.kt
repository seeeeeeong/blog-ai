package com.blog.ai.storage.article

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ArticleRepository : JpaRepository<ArticleEntity, Long> {
    fun existsByUrlHash(urlHash: String): Boolean

    @Query(
        value = "SELECT * FROM articles WHERE embedded_at IS NULL AND embed_error IS NULL ORDER BY id LIMIT :limit",
        nativeQuery = true,
    )
    fun findUnembedded(limit: Int): List<ArticleEntity>

    @Query(
        value = """
            SELECT a.id, a.title, a.content, a.url, a.published_at, b.company
            FROM articles a
            JOIN blogs b ON b.id = a.blog_id
            WHERE a.embedded_at IS NULL AND a.embed_error IS NULL
            ORDER BY a.id
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findUnembeddedSnapshots(limit: Int): List<Array<Any>>

    @Modifying
    @Query(
        value = """
            UPDATE articles
            SET embedded_at = NOW(), embed_error = NULL
            WHERE id = :id
        """,
        nativeQuery = true,
    )
    fun markEmbedded(id: Long)

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
            SELECT * FROM articles
            WHERE content IS NULL OR LENGTH(content) < :minLength
            ORDER BY id
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findShortContent(
        minLength: Int,
        limit: Int,
    ): List<ArticleEntity>

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
            SET embedded_at = NULL,
                embed_error = NULL,
                embed_retry_count = 0
            WHERE content IS NOT NULL
        """,
        nativeQuery = true,
    )
    fun resetAllEmbeddingsForReprocess(): Int

    @Modifying
    @Query(
        value = """
            UPDATE articles
            SET embedded_at = NULL,
                embed_error = NULL,
                embed_retry_count = 0
            WHERE id = :id
        """,
        nativeQuery = true,
    )
    fun resetEmbeddingForArticle(id: Long)
}
