package com.blog.ai.post.service

import com.blog.ai.global.text.EmbeddingBatcher
import com.blog.ai.post.model.PostChunkEmbedding
import com.blog.ai.post.model.PostEmbeddingResult
import com.blog.ai.post.model.PostEmbeddingSnapshot
import com.blog.ai.global.text.TextSplitter
import com.blog.ai.global.text.TokenTruncator
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.stereotype.Service

@Service
class PostEmbeddingWorker(
    private val embeddingModel: EmbeddingModel,
    private val postEmbeddingWriter: PostEmbeddingWriter,
) {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val MAX_EMBED_TOKENS = 7500
    }

    fun embedOne(snapshot: PostEmbeddingSnapshot): Boolean {
        val title = snapshot.title
        val content = snapshot.content ?: ""

        val docText = TokenTruncator.truncate("$title $content", MAX_EMBED_TOKENS)
        val chunks = if (content.isBlank()) emptyList() else TextSplitter.split(content)
        val chunkTexts = chunks.map { "$title\n\n$it" }

        val vectors = EmbeddingBatcher.embed(embeddingModel, listOf(docText) + chunkTexts)
        val docVector = EmbeddingBatcher.toVectorLiteral(vectors[0])
        val chunkCommands =
            chunks.mapIndexed { index, chunk ->
                PostChunkEmbedding(
                    postId = snapshot.postId,
                    chunkIndex = index,
                    content = chunk,
                    embedding = EmbeddingBatcher.toVectorLiteral(vectors[index + 1]),
                )
            }

        val command =
            PostEmbeddingResult(
                postId = snapshot.postId,
                title = title,
                url = snapshot.url,
                content = content,
                snapshotHash = snapshot.contentHash,
                docVector = docVector,
                chunks = chunkCommands,
            )
        val committed = postEmbeddingWriter.commit(command)
        if (committed) {
            log.debug { "BlogPost embedding completed: id=${snapshot.postId}, externalId=${snapshot.externalId}" }
        }
        return committed
    }

    fun recordError(
        postId: Long,
        snapshotHash: String?,
        message: String,
    ) {
        postEmbeddingWriter.recordError(postId, snapshotHash, message)
    }
}
