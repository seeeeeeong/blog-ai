package com.blog.ai.storage.article

import com.blog.ai.storage.blog.BlogEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "articles")
class ArticleEntity(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blog_id", nullable = false)
    val blog: BlogEntity = BlogEntity(),

    @Column(nullable = false, columnDefinition = "TEXT")
    val title: String = "",

    @Column(nullable = false, columnDefinition = "TEXT")
    val url: String = "",

    @Column(name = "url_hash", nullable = false, length = 64)
    val urlHash: String = "",

    @Column(columnDefinition = "TEXT")
    val content: String? = null,

    @Column(name = "published_at")
    val publishedAt: OffsetDateTime? = null,

    @Column(name = "crawled_at", nullable = false, updatable = false)
    val crawledAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "embed_error", columnDefinition = "TEXT")
    var embedError: String? = null,

    @Column(name = "embed_retry_count", nullable = false)
    var embedRetryCount: Int = 0,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
)
