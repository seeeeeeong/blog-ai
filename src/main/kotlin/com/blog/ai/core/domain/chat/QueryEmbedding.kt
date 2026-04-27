package com.blog.ai.core.domain.chat

internal data class QueryEmbedding(
    val text: String,
    val vector: String,
)
