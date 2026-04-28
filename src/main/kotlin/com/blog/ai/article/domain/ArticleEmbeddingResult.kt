package com.blog.ai.article.domain

import com.blog.ai.rag.domain.ChunkEmbedding

data class ArticleEmbeddingResult(
    val articleId: Long,
    val title: String,
    val url: String,
    val company: String,
    val content: String,
    val docVector: String,
    val chunks: List<ChunkEmbedding>,
)
