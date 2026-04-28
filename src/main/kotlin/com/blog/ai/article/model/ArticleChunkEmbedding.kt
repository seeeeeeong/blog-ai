package com.blog.ai.article.model

data class ArticleChunkEmbedding(
    val articleId: Long,
    val chunkIndex: Int,
    val content: String,
    val embedding: String,
)
