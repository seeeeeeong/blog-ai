package com.blog.ai.article

import com.blog.ai.blog.BlogEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.OffsetDateTime

@Entity
@Table(name = "articles")
class ArticleEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blog_id", nullable = false)
    val blog: BlogEntity,
    @Column(nullable = false, columnDefinition = "TEXT")
    val title: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    val url: String,
    @Column(name = "url_hash", nullable = false, length = 64)
    val urlHash: String,
    @Column(columnDefinition = "TEXT")
    val content: String? = null,
    @Column(name = "published_at")
    val publishedAt: OffsetDateTime? = null,
    @Column(name = "crawled_at", nullable = false, updatable = false)
    val crawledAt: OffsetDateTime = OffsetDateTime.now(),
    embedError: String? = null,
    embedRetryCount: Int = 0,
) {
    @Column(name = "embed_error", columnDefinition = "TEXT")
    var embedError: String? = embedError
        protected set

    @Column(name = "embed_retry_count", nullable = false)
    var embedRetryCount: Int = embedRetryCount
        protected set

    fun markEmbedError(error: String) {
        this.embedError = error
        this.embedRetryCount++
    }

    fun clearEmbedError() {
        this.embedError = null
    }

    companion object {
        fun create(
            blog: BlogEntity,
            title: String,
            url: String,
            urlHash: String,
            content: String? = null,
            publishedAt: OffsetDateTime? = null,
        ): ArticleEntity {
            require(title.isNotBlank()) { "Article title must not be blank" }
            require(url.isNotBlank()) { "Article url must not be blank" }
            require(urlHash.isNotBlank()) { "Article urlHash must not be blank" }
            return ArticleEntity(
                blog = blog,
                title = title,
                url = url,
                urlHash = urlHash,
                content = content,
                publishedAt = publishedAt,
            )
        }
    }
}

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
