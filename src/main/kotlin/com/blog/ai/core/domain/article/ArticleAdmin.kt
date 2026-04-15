package com.blog.ai.core.domain.article

import java.time.OffsetDateTime

data class ArticleAdmin(
    val id: Long,
    val title: String,
    val url: String,
    val urlHash: String,
    val company: String,
    val embedded: Boolean,
    val embedError: String?,
    val crawledAt: OffsetDateTime,
)
