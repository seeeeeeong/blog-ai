package com.blog.ai.core.domain.article

import java.time.OffsetDateTime

data class Article(
    val id: Long,
    val blogId: Long,
    val title: String,
    val url: String,
    val urlHash: String,
    val content: String?,
    val publishedAt: OffsetDateTime?,
    val crawledAt: OffsetDateTime,
)
