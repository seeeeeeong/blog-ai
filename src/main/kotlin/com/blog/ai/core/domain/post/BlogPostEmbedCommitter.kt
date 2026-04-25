package com.blog.ai.core.domain.post

import com.blog.ai.storage.post.BlogPostRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BlogPostEmbedCommitter(
    private val blogPostRepository: BlogPostRepository,
    private val blogPostChunkService: BlogPostChunkService,
) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Transactional
    fun commit(command: BlogPostEmbedCommitCommand): Boolean {
        val updated =
            blogPostRepository.updateEmbedding(
                command.postId,
                command.docVector,
                command.title,
                command.content,
                command.snapshotHash,
            )
        if (updated == 0) {
            log.info { "BlogPost embedding skipped (stale snapshot): id=${command.postId}" }
            return false
        }
        blogPostChunkService.replaceChunks(command.postId, command.chunks)
        return true
    }

    @Transactional
    fun recordError(
        postId: Long,
        snapshotHash: String?,
        message: String,
    ) {
        blogPostRepository.updateEmbedError(postId, message, snapshotHash)
    }
}
