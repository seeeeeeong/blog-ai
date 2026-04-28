package com.blog.ai.core.domain.article

import com.blog.ai.global.jdbc.JdbcTimeMapper
import com.blog.ai.global.text.EmbeddingBatcher
import com.blog.ai.global.text.TextSplitter
import com.blog.ai.global.text.TokenTruncator
import com.blog.ai.article.ArticleRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ArticleEmbedService(
    private val articleRepository: ArticleRepository,
    private val embeddingModel: EmbeddingModel,
    private val articleEmbedCommitter: ArticleEmbedCommitter,
    private val chunkContextEnricher: ChunkContextEnricher,
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

        val batch = embedBatch(snapshots) ?: return 0
        val embedded = commitAll(snapshots, batch)

        if (embedded > 0) {
            log.info { "Embedding processed: $embedded articles completed" }
        }
        return embedded
    }

    @Transactional
    fun clearRetriableErrors(maxRetries: Int = MAX_EMBED_RETRIES): Int =
        articleRepository.clearRetriableEmbedErrors(maxRetries)

    private fun embedBatch(snapshots: List<ArticleEmbedSnapshot>): ArticleEmbedBatch? {
        val docTexts = snapshots.map { TokenTruncator.truncate("${it.title} ${it.content}", MAX_EMBED_TOKENS) }
        val docVectors = runBatch("doc", docTexts, snapshots) ?: return null

        val chunkJobs = snapshots.map(::buildChunkJobs)
        val chunkTexts =
            snapshots.zip(chunkJobs).flatMap { (snap, jobs) -> jobs.map { it.embedText(snap.title) } }
        val chunkVectors =
            if (chunkTexts.isEmpty()) emptyList() else (runBatch("chunk", chunkTexts, snapshots) ?: return null)

        return ArticleEmbedBatch(docVectors, chunkJobs, chunkVectors)
    }

    private fun buildChunkJobs(snapshot: ArticleEmbedSnapshot): List<ChunkJob> {
        if (snapshot.content.isBlank()) return emptyList()
        val rawChunks = TextSplitter.split(snapshot.content)
        return rawChunks.map { rawChunk ->
            val context = chunkContextEnricher.enrich(snapshot.title, snapshot.content, rawChunk)
            ChunkJob(rawChunk = rawChunk, context = context)
        }
    }

    private fun commitAll(
        snapshots: List<ArticleEmbedSnapshot>,
        batch: ArticleEmbedBatch,
    ): Int {
        var cursor = 0
        var embedded = 0
        snapshots.forEachIndexed { i, snap ->
            val jobs = batch.chunkJobs[i]
            val commands = buildChunkCommands(snap.articleId, jobs, batch.chunkVectors, cursor)
            cursor += jobs.size
            if (commitOne(snap, batch.docVectors[i], commands)) embedded++
        }
        return embedded
    }

    private fun commitOne(
        snapshot: ArticleEmbedSnapshot,
        docVector: FloatArray,
        chunks: List<SaveChunkCommand>,
    ): Boolean =
        try {
            articleEmbedCommitter.commit(
                ArticleEmbedCommitCommand(
                    articleId = snapshot.articleId,
                    title = snapshot.title,
                    url = snapshot.url,
                    company = snapshot.company,
                    content = snapshot.content,
                    docVector = EmbeddingBatcher.toVectorLiteral(docVector),
                    chunks = chunks,
                ),
            )
            true
        } catch (e: Exception) {
            log.error(e) { "Embedding commit failed: id=${snapshot.articleId}" }
            articleEmbedCommitter.recordError(snapshot.articleId, e.message ?: "unknown")
            false
        }

    private fun buildChunkCommands(
        articleId: Long,
        jobs: List<ChunkJob>,
        chunkVectors: List<FloatArray>,
        cursor: Int,
    ): List<SaveChunkCommand> =
        jobs.mapIndexed { idx, job ->
            SaveChunkCommand(
                articleId = articleId,
                chunkIndex = idx,
                content = job.storedContent(),
                embedding = EmbeddingBatcher.toVectorLiteral(chunkVectors[cursor + idx]),
            )
        }

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
            publishedAt = JdbcTimeMapper.toOffsetDateTime(row[4]),
            company = row[5] as String,
        )
}
