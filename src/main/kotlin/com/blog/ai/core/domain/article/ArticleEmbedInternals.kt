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
    val chunkJobs: List<List<ChunkJob>>,
    val chunkVectors: List<FloatArray>,
)

internal data class ChunkJob(
    val rawChunk: String,
    val context: String?,
) {
    fun storedContent(): String = if (context != null) "$context\n\n$rawChunk" else rawChunk

    fun embedText(title: String): String =
        if (context != null) "$title\n\n$context\n\n$rawChunk" else "$title\n\n$rawChunk"
}

data class ArticleEmbedCommitCommand(
    val articleId: Long,
    val title: String,
    val url: String,
    val company: String,
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
