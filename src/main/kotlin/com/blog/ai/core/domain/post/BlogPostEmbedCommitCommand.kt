package com.blog.ai.core.domain.post

data class BlogPostEmbedCommitCommand(
    val postId: Long,
    val title: String,
    val content: String,
    val snapshotHash: String?,
    val docVector: String,
    val chunks: List<SaveBlogPostChunkCommand>,
)
