package com.blog.ai.core.domain.article

import com.blog.ai.storage.article.ArticleRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ArticleEmbedService(
    private val articleRepository: ArticleRepository,
    private val embeddingModel: EmbeddingModel,
    private val articleChunkService: ArticleChunkService,
) {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val DEFAULT_EMBED_LIMIT = 50
        const val MAX_EMBED_RETRIES = 5
    }

    @Transactional
    fun embedPending(limit: Int = DEFAULT_EMBED_LIMIT): Int {
        val articles = articleRepository.findUnembedded(limit)
        var embedded = 0

        for (article in articles) {
            val articleId = requireNotNull(article.id)
            try {
                val text = "${article.title} ${article.content ?: ""}"
                val response = embeddingModel.embed(text)
                val vector = response.joinToString(",", "[", "]")

                articleRepository.updateEmbedding(articleId, vector, text)

                val content = article.content?.takeIf { it.isNotBlank() }
                if (content != null) {
                    val metadata =
                        ChunkMetadata(
                            articleId = articleId,
                            title = article.title,
                            company = article.blog.company,
                            url = article.url,
                            publishedAt = article.publishedAt,
                        )
                    articleChunkService.saveChunks(metadata, content)
                }

                embedded++
                log.debug { "Embedding completed: id=$articleId, title=${article.title}" }
            } catch (e: Exception) {
                log.error(e) { "Embedding failed: id=$articleId" }
                articleRepository.updateEmbedError(articleId, e.message ?: "unknown")
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
}
