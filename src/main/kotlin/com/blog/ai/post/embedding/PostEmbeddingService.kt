package com.blog.ai.post.embedding

import com.blog.ai.post.PostEntity
import com.blog.ai.post.PostRepository
import com.blog.ai.post.embedding.PostEmbeddingResult
import com.blog.ai.post.embedding.PostEmbeddingSnapshot
import com.blog.ai.rag.embedding.model.DocumentEmbedding
import com.blog.ai.rag.embedding.model.EmbeddingDocument
import com.blog.ai.rag.embedding.service.EmbeddingPipeline
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PostEmbeddingService(
    private val postRepository: PostRepository,
    private val embeddingPipeline: EmbeddingPipeline,
    private val postEmbeddingWriter: PostEmbeddingWriter,
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
                if (embedOne(snapshot)) embedded++
            } catch (e: Exception) {
                log.error(e) { "Post embedding failed: id=${snapshot.postId}" }
                postEmbeddingWriter.recordError(snapshot.postId, snapshot.contentHash, e.message ?: "unknown")
            }
        }

        if (embedded > 0) {
            log.info { "Post embedding processed: $embedded posts completed" }
        }
        return embedded
    }

    @Transactional
    fun clearRetriableErrors(maxRetries: Int = MAX_EMBED_RETRIES): Int =
        postRepository.clearRetriableEmbedErrors(maxRetries)

    private fun embedOne(snapshot: PostEmbeddingSnapshot): Boolean {
        val embedding =
            embeddingPipeline.embedOne(snapshot.toDocument())
        val committed = postEmbeddingWriter.commit(snapshot.toResult(embedding))
        if (committed) {
            log.debug { "Post embedding completed: id=${snapshot.postId}, externalId=${snapshot.externalId}" }
        }
        return committed
    }

    private fun toSnapshot(entity: PostEntity): PostEmbeddingSnapshot =
        PostEmbeddingSnapshot(
            postId = requireNotNull(entity.id) { "PostEntity.id must not be null after load" },
            externalId = entity.externalId,
            title = entity.title,
            url = entity.url,
            content = entity.content,
            contentHash = entity.contentHash,
        )

    private fun PostEmbeddingSnapshot.toDocument(): EmbeddingDocument =
        EmbeddingDocument(
            id = postId,
            title = title,
            content = content ?: "",
        )

    private fun PostEmbeddingSnapshot.toResult(embedding: DocumentEmbedding): PostEmbeddingResult =
        PostEmbeddingResult(
            postId = postId,
            title = title,
            url = url,
            content = content ?: "",
            snapshotHash = contentHash,
            docVector = embedding.docVector,
            chunks = embedding.chunks,
        )
}
