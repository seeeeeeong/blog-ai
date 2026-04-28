package com.blog.ai.rag.embedding

data class ChunkEmbedding(
    val chunkIndex: Int,
    val content: String,
    val embedding: String,
)
