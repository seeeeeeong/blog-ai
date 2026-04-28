package com.blog.ai.post.infrastructure
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
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
}
