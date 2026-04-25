package com.blog.ai.core.domain.post

import com.blog.ai.storage.post.BlogPostEntity
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
        val snapshots = blogPostRepository.findUnembedded(limit).map(::toSnapshot)
        var embedded = 0

        for (snapshot in snapshots) {
            try {
                if (blogPostEmbedWorker.embedOne(snapshot)) embedded++
            } catch (e: Exception) {
                log.error(e) { "BlogPost embedding failed: id=${snapshot.postId}" }
                blogPostEmbedWorker.recordError(snapshot.postId, snapshot.contentHash, e.message ?: "unknown")
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

    private fun toSnapshot(entity: BlogPostEntity): BlogPostEmbedSnapshot =
        BlogPostEmbedSnapshot(
            postId = requireNotNull(entity.id) { "BlogPostEntity.id must not be null after load" },
            externalId = entity.externalId,
            title = entity.title,
            url = entity.url,
            content = entity.content,
            contentHash = entity.contentHash,
        )
}
