package com.blog.ai.core.domain.post

import com.blog.ai.core.domain.rag.RagChunkService
import com.blog.ai.post.PostRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BlogPostEmbedCommitter(
    private val blogPostRepository: PostRepository,
    private val ragChunkService: RagChunkService,
) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Transactional
    fun commit(command: BlogPostEmbedCommitCommand): Boolean {
        val updated = blogPostRepository.markEmbedded(command.postId, command.snapshotHash)
        if (updated == 0) {
            log.info { "BlogPost embedding skipped (stale snapshot): id=${command.postId}" }
            return false
        }
        ragChunkService.replaceAuthorPost(
            postId = command.postId,
            title = command.title,
            url = command.url,
            content = command.content,
            docVector = command.docVector,
            chunks = command.chunks,
        )
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
