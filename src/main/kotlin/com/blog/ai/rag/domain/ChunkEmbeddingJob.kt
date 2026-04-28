package com.blog.ai.rag.domain

data class ChunkEmbeddingJob(
    val rawChunk: String,
    val context: String?,
) {
    fun storedContent(): String = if (context != null) "$context\n\n$rawChunk" else rawChunk

    fun embedText(title: String): String =
        if (context != null) "$title\n\n$context\n\n$rawChunk" else "$title\n\n$rawChunk"
}
