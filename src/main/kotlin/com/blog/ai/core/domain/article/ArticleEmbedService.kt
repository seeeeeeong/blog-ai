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
        private const val DEFAULT_EMBED_LIMIT = 10
        const val MAX_EMBED_RETRIES = 5
    }

    @Transactional
    fun embedPending(limit: Int = DEFAULT_EMBED_LIMIT): Int {
        val articles = articleRepository.findUnembedded(limit)
        var embedded = 0

        for (article in articles) {
            try {
                val text = "${article.title} ${article.content ?: ""}"
                val response = embeddingModel.embed(text)
                val vector = response.joinToString(",", "[", "]")

                articleRepository.updateEmbedding(article.id, vector, text)

                val content = article.content?.takeIf { it.isNotBlank() }
                if (content != null) {
                    articleChunkService.saveChunks(article.id, article.title, content)
                }

                embedded++
                log.debug { "Embedding completed: id=${article.id}, title=${article.title}" }
            } catch (e: Exception) {
                log.error(e) { "Embedding failed: id=${article.id}" }
                articleRepository.updateEmbedError(article.id, e.message ?: "unknown")
            }
        }

        if (embedded > 0) {
            log.info { "Embedding processed: $embedded articles completed" }
        }
        return embedded
    }

    @Transactional
    fun clearRetriableErrors(maxRetries: Int = MAX_EMBED_RETRIES): Int {
        return articleRepository.clearRetriableEmbedErrors(maxRetries)
    }
}
