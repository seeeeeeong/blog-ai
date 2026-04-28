package com.blog.ai.rag.model

data class RagChunkWrite(
    val sourceType: RagSourceType,
    val sourceId: Long,
    val granularity: RagChunkGranularity,
    val chunkIndex: Int,
    val title: String,
    val url: String?,
    val company: String?,
    val content: String,
    val embedding: String,
    val metadataJson: String = "{}",
)
