package com.blog.ai.core.domain.similar

data class SimilarArticle(
    val id: Long,
    val title: String,
    val url: String,
    val company: String,
    val score: Double,
)
