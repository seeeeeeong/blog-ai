package com.blog.ai.core.domain.article

import java.time.OffsetDateTime

data class ChunkMetadata(
    val articleId: Long,
    val title: String,
    val company: String,
    val url: String,
    val publishedAt: OffsetDateTime?,
)
