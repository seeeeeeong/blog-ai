package com.blog.ai.rag

data class RagSearchQuery(
    val sourceType: RagSourceType,
    val granularity: RagChunkGranularity,
    val queryVector: String,
    val queryText: String,
    val candidatePoolSize: Int,
    val limit: Int,
)
