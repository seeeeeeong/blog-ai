package com.blog.ai.storage.post

import java.time.OffsetDateTime

data class CreateBlogPostCommand(
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
