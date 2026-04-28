package com.blog.ai.post.model

data class SimilarArticle(
    val id: Long,
    val title: String,
    val url: String,
    val company: String,
    val score: Double,
)
