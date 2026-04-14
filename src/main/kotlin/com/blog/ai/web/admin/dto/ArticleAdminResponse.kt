package com.blog.ai.web.admin.dto

import java.time.OffsetDateTime

data class ArticleAdminResponse(
    val id: Long,
    val title: String,
    val url: String,
    val urlHash: String,
    val company: String,
    val embedded: Boolean,
    val embedError: String?,
    val crawledAt: OffsetDateTime,
)
