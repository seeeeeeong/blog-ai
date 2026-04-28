package com.blog.ai.post

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.OffsetDateTime

@Entity
@Table(name = "blog_posts")
class PostEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "external_id", nullable = false, unique = true, length = 64)
    val externalId: String,
    title: String,
    content: String?,
    url: String?,
    author: String?,
    publishedAt: OffsetDateTime?,
    contentHash: String?,
    sourceUpdatedAt: OffsetDateTime,
    lastEventId: String?,
    isDeleted: Boolean = false,
    deletedAt: OffsetDateTime? = null,
) {
    @Column(nullable = false, columnDefinition = "TEXT")
    var title: String = title
        protected set

    @Column(columnDefinition = "TEXT")
    var content: String? = content
        protected set

    @Column(columnDefinition = "TEXT")
    var url: String? = url
        protected set

    @Column(length = 100)
    var author: String? = author
        protected set

    @Column(name = "published_at")
    var publishedAt: OffsetDateTime? = publishedAt
        protected set

    @Column(name = "content_hash", length = 64)
    var contentHash: String? = contentHash
        protected set

    @Column(name = "embed_error", columnDefinition = "TEXT")
    var embedError: String? = null
        protected set

    @Column(name = "embed_retry_count", nullable = false)
    var embedRetryCount: Int = 0
        protected set

    @Column(name = "source_updated_at", nullable = false)
    var sourceUpdatedAt: OffsetDateTime = sourceUpdatedAt
        protected set

    @Column(name = "last_event_id", length = 64)
    var lastEventId: String? = lastEventId
        protected set

    @Column(name = "synced_at", nullable = false)
    var syncedAt: OffsetDateTime = OffsetDateTime.now()
        protected set

    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = isDeleted
        protected set

    @Column(name = "deleted_at")
    var deletedAt: OffsetDateTime? = deletedAt
        protected set

    companion object {
        fun create(command: CreatePostCommand): PostEntity {
            require(command.externalId.isNotBlank()) { "Post externalId must not be blank" }
            require(command.title.isNotBlank()) { "Post title must not be blank" }
            return PostEntity(
                externalId = command.externalId,
                title = command.title,
                content = command.content,
                url = command.url,
                author = command.author,
                publishedAt = command.publishedAt,
                contentHash = command.contentHash,
                sourceUpdatedAt = command.sourceUpdatedAt,
                lastEventId = command.lastEventId,
                isDeleted = command.isDeleted,
                deletedAt = command.deletedAt,
            )
        }
    }
}

interface PostRepository : JpaRepository<PostEntity, Long> {
    fun findByExternalId(externalId: String): PostEntity?

    @Query(
        value = """
            SELECT * FROM blog_posts
            WHERE embedded_at IS NULL
              AND embed_error IS NULL
              AND is_deleted = false
            ORDER BY id
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findUnembedded(limit: Int): List<PostEntity>

    @Modifying
    @Query(
        value = """
            INSERT INTO blog_posts (
                external_id, title, content, url, author, published_at,
                content_hash, source_updated_at, last_event_id, synced_at,
                is_deleted, deleted_at, embed_retry_count
            )
            VALUES (
                :externalId, :title, :content, :url, :author, :publishedAt,
                :contentHash, :sourceUpdatedAt, :eventId, NOW(),
                false, NULL, 0
            )
            ON CONFLICT (external_id) DO UPDATE SET
                title = EXCLUDED.title,
                content = EXCLUDED.content,
                url = EXCLUDED.url,
                author = EXCLUDED.author,
                published_at = EXCLUDED.published_at,
                source_updated_at = EXCLUDED.source_updated_at,
                last_event_id = EXCLUDED.last_event_id,
                synced_at = NOW(),
                is_deleted = false,
                deleted_at = NULL,
                content_hash = EXCLUDED.content_hash,
                embedded_at = CASE
                    WHEN blog_posts.content_hash IS DISTINCT FROM EXCLUDED.content_hash
                        THEN NULL ELSE blog_posts.embedded_at END,
                embed_error = CASE
                    WHEN blog_posts.content_hash IS DISTINCT FROM EXCLUDED.content_hash
                        THEN NULL ELSE blog_posts.embed_error END,
                embed_retry_count = CASE
                    WHEN blog_posts.content_hash IS DISTINCT FROM EXCLUDED.content_hash
                        THEN 0 ELSE blog_posts.embed_retry_count END
            WHERE EXCLUDED.source_updated_at > blog_posts.source_updated_at
        """,
        nativeQuery = true,
    )
    fun upsert(
        externalId: String,
        title: String,
        content: String?,
        url: String?,
        author: String?,
        publishedAt: OffsetDateTime?,
        contentHash: String,
        sourceUpdatedAt: OffsetDateTime,
        eventId: String?,
    ): Int

    @Modifying
    @Query(
        value = """
            INSERT INTO blog_posts (
                external_id, title, content, url, author, published_at,
                content_hash, source_updated_at, last_event_id, synced_at,
                is_deleted, deleted_at, embed_retry_count
            )
            VALUES (
                :externalId, '', NULL, NULL, NULL, NULL,
                NULL, :sourceUpdatedAt, :eventId, NOW(),
                true, NOW(), 0
            )
            ON CONFLICT (external_id) DO UPDATE SET
                is_deleted = true,
                deleted_at = NOW(),
                source_updated_at = EXCLUDED.source_updated_at,
                last_event_id = EXCLUDED.last_event_id,
                synced_at = NOW()
            WHERE EXCLUDED.source_updated_at > blog_posts.source_updated_at
        """,
        nativeQuery = true,
    )
    fun softDelete(
        externalId: String,
        sourceUpdatedAt: OffsetDateTime,
        eventId: String?,
    ): Int

    @Modifying
    @Query(
        value = """
            UPDATE blog_posts
            SET embedded_at = NOW(),
                embed_error = NULL
            WHERE id = :id
              AND content_hash IS NOT DISTINCT FROM :contentHash
              AND is_deleted = false
        """,
        nativeQuery = true,
    )
    fun markEmbedded(
        id: Long,
        contentHash: String?,
    ): Int

    @Modifying
    @Query(
        value = """
            UPDATE blog_posts
            SET embed_error = :error,
                embed_retry_count = embed_retry_count + 1
            WHERE id = :id
              AND content_hash IS NOT DISTINCT FROM :contentHash
              AND is_deleted = false
        """,
        nativeQuery = true,
    )
    fun updateEmbedError(
        id: Long,
        error: String,
        contentHash: String?,
    ): Int

    @Modifying
    @Query(
        value = """
            UPDATE blog_posts SET embed_error = NULL
            WHERE embed_error IS NOT NULL AND embed_retry_count <= :maxRetries
        """,
        nativeQuery = true,
    )
    fun clearRetriableEmbedErrors(maxRetries: Int): Int

    @Query(value = "SELECT COUNT(*) FROM blog_posts WHERE is_deleted = false", nativeQuery = true)
    fun countActive(): Long
}

data class CreatePostCommand(
    val externalId: String,
    val title: String,
    val content: String?,
    val url: String?,
    val author: String?,
    val publishedAt: OffsetDateTime?,
    val contentHash: String?,
    val sourceUpdatedAt: OffsetDateTime,
    val lastEventId: String?,
    val isDeleted: Boolean = false,
    val deletedAt: OffsetDateTime? = null,
)
