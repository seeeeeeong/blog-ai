package com.blog.ai.core.domain.post

import com.blog.ai.core.support.text.TextSplitter
import com.blog.ai.storage.post.BlogPostChunkRepository
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class BlogPostChunkService(
    private val blogPostChunkRepository: BlogPostChunkRepository,
    private val embeddingModel: EmbeddingModel,
) {
    @Transactional
    fun saveChunks(
        postId: Long,
        title: String,
        content: String,
    ) {
        val chunks = TextSplitter.split(content)
        val prepared =
            chunks.mapIndexed { index, chunk ->
                val vector = embeddingModel.embed("$title\n\n$chunk").joinToString(",", "[", "]")
                SaveBlogPostChunkCommand(
                    postId = postId,
                    chunkIndex = index,
                    content = chunk,
                    embedding = vector,
                )
            }

        blogPostChunkRepository.deleteByPostId(postId)
        prepared.forEach(blogPostChunkRepository::saveChunk)
    }
}
