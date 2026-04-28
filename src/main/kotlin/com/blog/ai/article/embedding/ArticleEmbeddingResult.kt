package com.blog.ai.article.embedding

import com.blog.ai.rag.embedding.ChunkEmbedding

data class ArticleEmbeddingResult(
    val articleId: Long,
    val title: String,
    val url: String,
    val company: String,
    val content: String,
    val docVector: String,
    val chunks: List<ChunkEmbedding>,
)
