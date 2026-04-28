package com.blog.ai.post.model

import java.time.OffsetDateTime

data class SyncPost(
    val externalId: String,
    val title: String,
    val content: String?,
    val url: String?,
    val author: String?,
    val publishedAt: OffsetDateTime?,
    val sourceUpdatedAt: OffsetDateTime,
    val eventId: String?,
)
