package com.blog.ai.core.domain.post

import com.blog.ai.storage.post.BlogPostRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class BlogPostEmbedService(
    private val blogPostRepository: BlogPostRepository,
    private val embeddingModel: EmbeddingModel,
    private val blogPostChunkService: BlogPostChunkService,
) {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val DEFAULT_EMBED_LIMIT = 50
        const val MAX_EMBED_RETRIES = 5
        private const val MAX_EMBED_CONTENT_LENGTH = 6000
    }

    @Transactional
    fun embedPending(limit: Int = DEFAULT_EMBED_LIMIT): Int {
        val posts = blogPostRepository.findUnembedded(limit)
        var embedded = 0

        for (post in posts) {
            val postId = requireNotNull(post.id)
            val snapshotHash = post.contentHash
            try {
                val title = post.title
                val content = post.content ?: ""
                val embedText = "$title ${content.take(MAX_EMBED_CONTENT_LENGTH)}"
                val response = embeddingModel.embed(embedText)
                val vector = response.joinToString(",", "[", "]")

                val updated = blogPostRepository.updateEmbedding(postId, vector, title, content, snapshotHash)
                if (updated == 0) {
                    log.info { "BlogPost embedding skipped (stale snapshot): id=$postId" }
                    continue
                }

                if (content.isNotBlank()) {
                    blogPostChunkService.saveChunks(postId, title, content)
                }

                embedded++
                log.debug { "BlogPost embedding completed: id=$postId, externalId=${post.externalId}" }
            } catch (e: Exception) {
                log.error(e) { "BlogPost embedding failed: id=$postId" }
                blogPostRepository.updateEmbedError(postId, e.message ?: "unknown", snapshotHash)
            }
        }

        if (embedded > 0) {
            log.info { "BlogPost embedding processed: $embedded posts completed" }
        }
        return embedded
    }

    @Transactional
    fun clearRetriableErrors(maxRetries: Int = MAX_EMBED_RETRIES): Int =
        blogPostRepository.clearRetriableEmbedErrors(maxRetries)
}
