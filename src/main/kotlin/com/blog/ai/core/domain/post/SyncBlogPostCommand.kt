package com.blog.ai.core.domain.post

import java.time.OffsetDateTime

data class SyncBlogPostCommand(
    val externalId: String,
    val title: String,
    val content: String?,
    val url: String?,
    val author: String?,
    val publishedAt: OffsetDateTime?,
    val sourceUpdatedAt: OffsetDateTime,
    val eventId: String?,
)
