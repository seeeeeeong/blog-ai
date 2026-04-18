package com.blog.ai.storage.article

import com.blog.ai.core.domain.article.Article

fun ArticleEntity.toArticle() =
    Article(
        id = requireNotNull(id) { "ArticleEntity.id must not be null after persistence" },
        blogId = requireNotNull(blog.id) { "BlogEntity.id must not be null after persistence" },
        title = title,
        url = url,
        urlHash = urlHash,
        content = content,
        publishedAt = publishedAt,
        crawledAt = crawledAt,
    )
