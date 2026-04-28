package com.blog.ai.post.domain

import com.blog.ai.rag.domain.ChunkEmbedding

data class PostEmbeddingResult(
    val postId: Long,
    val title: String,
    val url: String?,
    val content: String,
    val snapshotHash: String?,
    val docVector: String,
    val chunks: List<ChunkEmbedding>,
)
