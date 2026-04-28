package com.blog.ai.post

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

    private fun toSnapshot(entity: PostEntity): PostEmbedSnapshot =
        PostEmbedSnapshot(
            postId = requireNotNull(entity.id) { "PostEntity.id must not be null after load" },
            externalId = entity.externalId,
            title = entity.title,
            url = entity.url,
            content = entity.content,
            contentHash = entity.contentHash,
        )
}

data class PostEmbedSnapshot(
    val postId: Long,
    val externalId: String,
    val title: String,
    val url: String?,
    val content: String?,
    val contentHash: String?,
)

data class PostEmbedCommitCommand(
    val postId: Long,
    val title: String,
    val url: String?,
    val content: String,
    val snapshotHash: String?,
    val docVector: String,
    val chunks: List<SavePostChunkCommand>,
)

data class SavePostChunkCommand(
    val postId: Long,
    val chunkIndex: Int,
    val content: String,
    val embedding: String,
)
