package com.blog.ai.storage.article

import com.blog.ai.core.domain.article.Article

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
