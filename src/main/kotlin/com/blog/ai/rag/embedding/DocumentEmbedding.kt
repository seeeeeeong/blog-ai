package com.blog.ai.rag.embedding

data class DocumentEmbedding(
    val docVector: String,
    val chunks: List<ChunkEmbedding>,
)
