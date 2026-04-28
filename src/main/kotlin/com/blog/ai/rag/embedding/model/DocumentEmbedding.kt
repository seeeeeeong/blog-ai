package com.blog.ai.rag.embedding.model

data class DocumentEmbedding(
    val docVector: String,
    val chunks: List<ChunkEmbedding>,
)
