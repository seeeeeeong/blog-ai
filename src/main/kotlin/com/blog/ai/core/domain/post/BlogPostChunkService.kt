package com.blog.ai.core.domain.post

import com.blog.ai.storage.post.BlogPostChunkRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BlogPostChunkService(
    private val blogPostChunkRepository: BlogPostChunkRepository,
) {
    @Transactional
    fun replaceChunks(
        postId: Long,
        chunks: List<SaveBlogPostChunkCommand>,
    ) {
        blogPostChunkRepository.deleteByPostId(postId)
        chunks.forEach(blogPostChunkRepository::saveChunk)
    }
}
