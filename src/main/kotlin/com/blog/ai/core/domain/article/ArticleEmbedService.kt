package com.blog.ai.core.domain.article

import com.blog.ai.core.support.jdbc.JdbcTimestamps
import com.blog.ai.core.support.text.EmbeddingBatcher
import com.blog.ai.core.support.text.TextSplitter
import com.blog.ai.core.support.text.TokenTruncator
import com.blog.ai.storage.article.ArticleRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class ArticleEmbedService(
    private val articleRepository: ArticleRepository,
    private val embeddingModel: EmbeddingModel,
    private val articleEmbedCommitter: ArticleEmbedCommitter,
) {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val DEFAULT_EMBED_LIMIT = 50
        const val MAX_EMBED_RETRIES = 5
        private const val MAX_EMBED_TOKENS = 7500
    }

    fun embedPending(limit: Int = DEFAULT_EMBED_LIMIT): Int {
        val snapshots = articleRepository.findUnembeddedSnapshots(limit).map(::toSnapshot)
        if (snapshots.isEmpty()) return 0

        val docTexts = snapshots.map { TokenTruncator.truncate("${it.title} ${it.content}", MAX_EMBED_TOKENS) }
        val docVectors =
            runBatch("doc", docTexts, snapshots) ?: return 0

        val chunkJobs =
            snapshots.map { if (it.content.isBlank()) emptyList() else TextSplitter.split(it.content) }
        val chunkTexts =
            snapshots.zip(chunkJobs).flatMap { (snap, chunks) -> chunks.map { "${snap.title}\n\n$it" } }
        val chunkVectors =
            if (chunkTexts.isEmpty()) emptyList() else (runBatch("chunk", chunkTexts, snapshots) ?: return 0)

        var cursor = 0
        var embedded = 0
        snapshots.forEachIndexed { i, snap ->
            val jobs = chunkJobs[i]
            val commands =
                jobs.mapIndexed { idx, chunk ->
                    SaveChunkCommand(
                        articleId = snap.articleId,
                        chunkIndex = idx,
                        content = chunk,
                        embedding = EmbeddingBatcher.toVectorLiteral(chunkVectors[cursor + idx]),
                    )
                }
            cursor += jobs.size

            try {
                articleEmbedCommitter.commit(
                    ArticleEmbedCommitCommand(
                        articleId = snap.articleId,
                        title = snap.title,
                        content = snap.content,
                        docVector = EmbeddingBatcher.toVectorLiteral(docVectors[i]),
                        chunks = commands,
                    ),
                )
                embedded++
            } catch (e: Exception) {
                log.error(e) { "Embedding commit failed: id=${snap.articleId}" }
                articleEmbedCommitter.recordError(snap.articleId, e.message ?: "unknown")
            }
        }

        if (embedded > 0) {
            log.info { "Embedding processed: $embedded articles completed" }
        }
        return embedded
    }

    @Transactional
    fun clearRetriableErrors(maxRetries: Int = MAX_EMBED_RETRIES): Int =
        articleRepository.clearRetriableEmbedErrors(maxRetries)

    private fun runBatch(
        kind: String,
        texts: List<String>,
        snapshots: List<ArticleEmbedSnapshot>,
    ): List<FloatArray>? =
        try {
            EmbeddingBatcher.embed(embeddingModel, texts)
        } catch (e: Exception) {
            log.error(e) { "Batch $kind embedding failed: size=${texts.size}" }
            snapshots.forEach { articleEmbedCommitter.recordError(it.articleId, e.message ?: "unknown") }
            null
        }

    private fun toSnapshot(row: Array<Any>): ArticleEmbedSnapshot =
        ArticleEmbedSnapshot(
            articleId = (row[0] as Number).toLong(),
            title = row[1] as String,
            content = (row[2] as String?) ?: "",
            url = row[3] as String,
            publishedAt = JdbcTimestamps.toOffsetDateTime(row[4]),
            company = row[5] as String,
        )
}

data class ArticleEmbedSnapshot(
    val articleId: Long,
    val title: String,
    val content: String,
    val url: String,
    val publishedAt: OffsetDateTime?,
    val company: String,
)
