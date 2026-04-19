package com.blog.ai.core.domain.post

import com.blog.ai.storage.post.BlogPostEntity
import com.blog.ai.storage.post.BlogPostRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BlogPostEmbedWorker(
    private val blogPostRepository: BlogPostRepository,
    private val embeddingModel: EmbeddingModel,
    private val blogPostChunkService: BlogPostChunkService,
) {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val MAX_EMBED_CONTENT_LENGTH = 6000
    }

    @Transactional
    fun embedOne(post: BlogPostEntity): Boolean {
        val postId = requireNotNull(post.id)
        val snapshotHash = post.contentHash
        val title = post.title
        val content = post.content ?: ""
        val embedText = "$title ${content.take(MAX_EMBED_CONTENT_LENGTH)}"
        val vector = embeddingModel.embed(embedText).joinToString(",", "[", "]")

        val updated = blogPostRepository.updateEmbedding(postId, vector, title, content, snapshotHash)
        if (updated == 0) {
            log.info { "BlogPost embedding skipped (stale snapshot): id=$postId" }
            return false
        }

        if (content.isNotBlank()) {
            blogPostChunkService.saveChunks(postId, title, content)
        }

        log.debug { "BlogPost embedding completed: id=$postId, externalId=${post.externalId}" }
        return true
    }

    @Transactional
    fun recordError(
        postId: Long,
        contentHash: String?,
        message: String,
    ) {
        blogPostRepository.updateEmbedError(postId, message, contentHash)
    }
}
