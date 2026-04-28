package com.blog.ai.rag.embedding.model

data class EmbeddingDocument(
    val id: Long,
    val title: String,
    val content: String,
)
