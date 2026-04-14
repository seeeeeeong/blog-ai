package com.blog.ai.storage.article

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

fun ArticleEntity.toArticle() = Article(
    id = id,
    blogId = blog.id,
    title = title,
    url = url,
    urlHash = urlHash,
    content = content,
    publishedAt = publishedAt,
    crawledAt = crawledAt,
)
