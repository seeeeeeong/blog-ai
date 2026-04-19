package com.blog.ai.core.domain.post

import com.blog.ai.core.support.text.TextSplitter
import com.blog.ai.storage.post.BlogPostChunkRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class BlogPostChunkService(
    private val blogPostChunkRepository: BlogPostChunkRepository,
    private val embeddingModel: EmbeddingModel,
) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Transactional
    fun saveChunks(
        postId: Long,
        title: String,
        content: String,
    ) {
        blogPostChunkRepository.deleteByPostId(postId)

        val chunks = TextSplitter.split(content)
        chunks.forEachIndexed { index, chunk ->
            val embedText = "$title\n\n$chunk"
            val vector =
                try {
                    embeddingModel.embed(embedText).joinToString(",", "[", "]")
                } catch (e: Exception) {
                    log.warn(e) { "Chunk embedding failed: postId=$postId, index=$index" }
                    return@forEachIndexed
                }

            blogPostChunkRepository.saveChunk(
                SaveBlogPostChunkCommand(
                    postId = postId,
                    chunkIndex = index,
                    content = chunk,
                    embedding = vector,
                ),
            )
        }
    }
}
