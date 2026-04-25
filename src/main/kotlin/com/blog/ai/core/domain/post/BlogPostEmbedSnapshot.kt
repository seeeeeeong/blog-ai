package com.blog.ai.core.domain.post

data class BlogPostEmbedSnapshot(
    val postId: Long,
    val externalId: String,
    val title: String,
    val content: String?,
    val contentHash: String?,
)
