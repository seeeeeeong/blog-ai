package com.blog.ai.storage.blog

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "blogs")
class BlogEntity(
    @Column(nullable = false, length = 100)
    val name: String = "",

    @Column(nullable = false, length = 100)
    val company: String = "",

    @Column(name = "rss_url", nullable = false, length = 500)
    val rssUrl: String = "",

    @Column(name = "home_url", nullable = false, length = 500)
    val homeUrl: String = "",

    @Column(nullable = false)
    val active: Boolean = true,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
)
