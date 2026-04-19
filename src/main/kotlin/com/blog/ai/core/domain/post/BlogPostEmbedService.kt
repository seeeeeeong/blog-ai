package com.blog.ai.core.domain.post

import com.blog.ai.storage.post.BlogPostRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BlogPostEmbedService(
    private val blogPostRepository: BlogPostRepository,
    private val blogPostEmbedWorker: BlogPostEmbedWorker,
) {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val DEFAULT_EMBED_LIMIT = 50
        const val MAX_EMBED_RETRIES = 5
    }

    fun embedPending(limit: Int = DEFAULT_EMBED_LIMIT): Int {
        val posts = blogPostRepository.findUnembedded(limit)
        var embedded = 0

        for (post in posts) {
            val postId = requireNotNull(post.id)
            try {
                if (blogPostEmbedWorker.embedOne(post)) embedded++
            } catch (e: Exception) {
                log.error(e) { "BlogPost embedding failed: id=$postId" }
                blogPostEmbedWorker.recordError(postId, post.contentHash, e.message ?: "unknown")
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
