package com.blog.ai.post.service

import com.blog.ai.post.entity.PostEntity
import com.blog.ai.post.model.PostEmbeddingSnapshot
import com.blog.ai.post.repository.PostRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PostEmbeddingService(
    private val postRepository: PostRepository,
    private val postEmbeddingWorker: PostEmbeddingWorker,
) {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val DEFAULT_EMBED_LIMIT = 50
        const val MAX_EMBED_RETRIES = 5
    }

    fun embedPending(limit: Int = DEFAULT_EMBED_LIMIT): Int {
        val snapshots = postRepository.findUnembedded(limit).map(::toSnapshot)
        var embedded = 0

        for (snapshot in snapshots) {
            try {
                if (postEmbeddingWorker.embedOne(snapshot)) embedded++
            } catch (e: Exception) {
                log.error(e) { "BlogPost embedding failed: id=${snapshot.postId}" }
                postEmbeddingWorker.recordError(snapshot.postId, snapshot.contentHash, e.message ?: "unknown")
            }
        }

        if (embedded > 0) {
            log.info { "BlogPost embedding processed: $embedded posts completed" }
        }
        return embedded
    }

    @Transactional
    fun clearRetriableErrors(maxRetries: Int = MAX_EMBED_RETRIES): Int =
        postRepository.clearRetriableEmbedErrors(maxRetries)

    private fun toSnapshot(entity: PostEntity): PostEmbeddingSnapshot =
        PostEmbeddingSnapshot(
            postId = requireNotNull(entity.id) { "PostEntity.id must not be null after load" },
            externalId = entity.externalId,
            title = entity.title,
            url = entity.url,
            content = entity.content,
            contentHash = entity.contentHash,
        )
}
