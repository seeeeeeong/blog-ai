package com.blog.ai.post.model

data class PostChunkEmbedding(
    val postId: Long,
    val chunkIndex: Int,
    val content: String,
    val embedding: String,
)
