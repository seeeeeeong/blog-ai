package com.blog.ai.core.domain.article

data class SaveChunkCommand(
    val articleId: Long,
    val chunkIndex: Int,
    val content: String,
)
