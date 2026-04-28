package com.blog.ai.article.model

data class ArticleEmbeddingBatch(
    val docVectors: List<FloatArray>,
    val chunkJobs: List<List<ArticleChunkJob>>,
    val chunkVectors: List<FloatArray>,
)
