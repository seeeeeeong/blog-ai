package com.blog.ai.core.domain.post

data class BlogPostEmbedSnapshot(
    val postId: Long,
    val externalId: String,
    val title: String,
    val content: String?,
    val contentHash: String?,
)

data class BlogPostEmbedCommitCommand(
    val postId: Long,
    val title: String,
    val content: String,
    val snapshotHash: String?,
    val docVector: String,
    val chunks: List<SaveBlogPostChunkCommand>,
)

data class SaveBlogPostChunkCommand(
    val postId: Long,
    val chunkIndex: Int,
    val content: String,
    val embedding: String,
)
