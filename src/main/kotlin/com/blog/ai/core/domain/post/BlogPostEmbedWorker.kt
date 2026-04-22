package com.blog.ai.core.domain.post

import com.blog.ai.core.support.text.EmbeddingBatcher
import com.blog.ai.core.support.text.TextSplitter
import com.blog.ai.core.support.text.TokenTruncator
import com.blog.ai.storage.post.BlogPostEntity
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.stereotype.Service

@Service
class BlogPostEmbedWorker(
    private val embeddingModel: EmbeddingModel,
    private val blogPostEmbedCommitter: BlogPostEmbedCommitter,
) {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val MAX_EMBED_TOKENS = 7500
    }

    fun embedOne(post: BlogPostEntity): Boolean {
        val postId = requireNotNull(post.id)
        val snapshotHash = post.contentHash
        val title = post.title
        val content = post.content ?: ""

        val docText = TokenTruncator.truncate("$title $content", MAX_EMBED_TOKENS)
        val chunks = if (content.isBlank()) emptyList() else TextSplitter.split(content)
        val chunkTexts = chunks.map { "$title\n\n$it" }

        val vectors = EmbeddingBatcher.embed(embeddingModel, listOf(docText) + chunkTexts)
        val docVector = EmbeddingBatcher.toVectorLiteral(vectors[0])
        val chunkCommands =
            chunks.mapIndexed { index, chunk ->
                SaveBlogPostChunkCommand(
                    postId = postId,
                    chunkIndex = index,
                    content = chunk,
                    embedding = EmbeddingBatcher.toVectorLiteral(vectors[index + 1]),
                )
            }

        val command =
            BlogPostEmbedCommitCommand(
                postId = postId,
                title = title,
                content = content,
                snapshotHash = snapshotHash,
                docVector = docVector,
                chunks = chunkCommands,
            )
        val committed = blogPostEmbedCommitter.commit(command)
        if (committed) {
            log.debug { "BlogPost embedding completed: id=$postId, externalId=${post.externalId}" }
        }
        return committed
    }

    fun recordError(
        postId: Long,
        snapshotHash: String?,
        message: String,
    ) {
        blogPostEmbedCommitter.recordError(postId, snapshotHash, message)
    }
}
