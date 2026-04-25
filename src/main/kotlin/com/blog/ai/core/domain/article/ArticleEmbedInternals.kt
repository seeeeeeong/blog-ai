package com.blog.ai.core.domain.article

import java.time.OffsetDateTime

internal data class ArticleEmbedSnapshot(
    val articleId: Long,
    val title: String,
    val content: String,
    val url: String,
    val publishedAt: OffsetDateTime?,
    val company: String,
)

internal data class ArticleEmbedBatch(
    val docVectors: List<FloatArray>,
    val chunkJobs: List<List<String>>,
    val chunkVectors: List<FloatArray>,
)

data class ArticleEmbedCommitCommand(
    val articleId: Long,
    val title: String,
    val content: String,
    val docVector: String,
    val chunks: List<SaveChunkCommand>,
)

data class SaveChunkCommand(
    val articleId: Long,
    val chunkIndex: Int,
    val content: String,
    val embedding: String,
)
