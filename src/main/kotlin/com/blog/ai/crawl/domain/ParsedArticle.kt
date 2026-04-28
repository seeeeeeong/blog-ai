package com.blog.ai.crawl.domain

import java.time.Instant

data class ParsedArticle(
    val title: String,
    val url: String,
    val urlHash: String,
    val content: String?,
    val publishedAt: Instant?,
)
