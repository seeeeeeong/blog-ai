package com.blog.ai.post.embedding

import com.blog.ai.rag.embedding.model.ChunkEmbedding

data class PostEmbeddingResult(
    val postId: Long,
    val title: String,
    val url: String?,
    val content: String,
    val snapshotHash: String?,
    val docVector: String,
    val chunks: List<ChunkEmbedding>,
)
