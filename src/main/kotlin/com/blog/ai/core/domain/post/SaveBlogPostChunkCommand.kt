package com.blog.ai.core.domain.post

data class SaveBlogPostChunkCommand(
    val postId: Long,
    val chunkIndex: Int,
    val content: String,
    val embedding: String,
)
