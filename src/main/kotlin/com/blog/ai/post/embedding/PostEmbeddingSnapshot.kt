package com.blog.ai.post.embedding

data class PostEmbeddingSnapshot(
    val postId: Long,
    val externalId: String,
    val title: String,
    val url: String?,
    val content: String?,
    val contentHash: String?,
)
