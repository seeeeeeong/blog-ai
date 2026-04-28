package com.blog.ai.article.application.embedding

import com.blog.ai.article.domain.ArticleEmbeddingResult
import com.blog.ai.article.domain.ArticleEmbeddingSnapshot
import com.blog.ai.article.infrastructure.ArticleRepository
import com.blog.ai.global.persistence.JdbcTimeMapper
import com.blog.ai.rag.application.embedding.EmbeddingPipeline
import com.blog.ai.rag.domain.DocumentEmbedding
import com.blog.ai.rag.domain.EmbeddingDocument
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ArticleEmbeddingService(
    private val articleRepository: ArticleRepository,
    private val embeddingPipeline: EmbeddingPipeline,
    private val articleEmbeddingWriter: ArticleEmbeddingWriter,
) {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val DEFAULT_EMBED_LIMIT = 50
        const val MAX_EMBED_RETRIES = 5
    }

    fun embedPending(limit: Int = DEFAULT_EMBED_LIMIT): Int {
        val snapshots = articleRepository.findUnembeddedSnapshots(limit).map(::toSnapshot)
        if (snapshots.isEmpty()) return 0

        val embeddings =
            embeddingPipeline.embedBatchWithChunkEnrichment(
                documents = snapshots.map { it.toDocument() },
                onError = { document, message -> articleEmbeddingWriter.recordError(document.id, message) },
            ) ?: return 0
        val embedded = commitAll(snapshots, embeddings)

        if (embedded > 0) {
            log.info { "Embedding processed: $embedded articles completed" }
        }
        return embedded
    }

    @Transactional
    fun clearRetriableErrors(maxRetries: Int = MAX_EMBED_RETRIES): Int =
        articleRepository.clearRetriableEmbedErrors(maxRetries)

    private fun commitAll(
        snapshots: List<ArticleEmbeddingSnapshot>,
        embeddings: List<DocumentEmbedding>,
    ): Int {
        var embedded = 0
        snapshots.forEachIndexed { i, snap ->
            if (commitOne(snap, embeddings[i])) embedded++
        }
        return embedded
    }

    private fun commitOne(
        snapshot: ArticleEmbeddingSnapshot,
        embedding: DocumentEmbedding,
    ): Boolean =
        try {
            articleEmbeddingWriter.commit(
                ArticleEmbeddingResult(
                    articleId = snapshot.articleId,
                    title = snapshot.title,
                    url = snapshot.url,
                    company = snapshot.company,
                    content = snapshot.content,
                    docVector = embedding.docVector,
                    chunks = embedding.chunks,
                ),
            )
            true
        } catch (e: Exception) {
            log.error(e) { "Embedding commit failed: id=${snapshot.articleId}" }
            articleEmbeddingWriter.recordError(snapshot.articleId, e.message ?: "unknown")
            false
        }

    private fun toSnapshot(row: Array<Any>): ArticleEmbeddingSnapshot =
        ArticleEmbeddingSnapshot(
            articleId = (row[0] as Number).toLong(),
            title = row[1] as String,
            content = (row[2] as String?) ?: "",
            url = row[3] as String,
            publishedAt = JdbcTimeMapper.toOffsetDateTime(row[4]),
            company = row[5] as String,
        )

    private fun ArticleEmbeddingSnapshot.toDocument(): EmbeddingDocument =
        EmbeddingDocument(
            id = articleId,
            title = title,
            content = content,
        )
}
