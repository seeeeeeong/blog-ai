package com.blog.ai.article.domain

import java.time.OffsetDateTime

data class ArticleEmbeddingSnapshot(
    val articleId: Long,
    val title: String,
    val content: String,
    val url: String,
    val publishedAt: OffsetDateTime?,
    val company: String,
)
