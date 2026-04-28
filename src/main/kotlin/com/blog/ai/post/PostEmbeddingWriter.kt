package com.blog.ai.post

import com.blog.ai.post.PostRepository
import com.blog.ai.rag.service.RagService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PostEmbeddingWriter(
    private val postRepository: PostRepository,
    private val ragService: RagService,
) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Transactional
    fun commit(command: PostEmbeddingResult): Boolean {
        val updated = postRepository.markEmbedded(command.postId, command.snapshotHash)
        if (updated == 0) {
            log.info { "BlogPost embedding skipped (stale snapshot): id=${command.postId}" }
            return false
        }
        ragService.replaceAuthorPost(
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
        postRepository.updateEmbedError(postId, message, snapshotHash)
    }
}
