package com.blog.ai.rag.domain

data class ChunkEmbedding(
    val chunkIndex: Int,
    val content: String,
    val embedding: String,
)
