package com.blog.ai.post.model

import java.time.OffsetDateTime

data class CreatePost(
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
