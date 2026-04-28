package com.blog.ai.rag.domain

data class DocumentEmbedding(
    val docVector: String,
    val chunks: List<ChunkEmbedding>,
)
