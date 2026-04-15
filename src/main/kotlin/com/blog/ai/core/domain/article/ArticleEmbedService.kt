package com.blog.ai.core.domain.article

import com.blog.ai.storage.article.ArticleRepository
import org.slf4j.LoggerFactory
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ArticleEmbedService(
    private val articleRepository: ArticleRepository,
    private val embeddingModel: EmbeddingModel,
    private val articleChunkService: ArticleChunkService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun embedPending(limit: Int = 10): Int {
        val articles = articleRepository.findUnembedded(limit)
        var embedded = 0

        for (article in articles) {
            try {
                val text = "${article.title} ${article.content ?: ""}"
                val response = embeddingModel.embed(text)
                val vector = response.joinToString(",", "[", "]")

                articleRepository.updateEmbedding(article.id, vector, text)

                if (!article.content.isNullOrBlank()) {
                    articleChunkService.saveChunks(article.id, article.title, article.content!!)
                }

                embedded++
                log.debug("Embedding completed: id={}, title={}", article.id, article.title)
            } catch (e: Exception) {
                log.error("Embedding failed: id={}, error={}", article.id, e.message)
                articleRepository.updateEmbedError(article.id, e.message ?: "unknown")
            }
        }

        if (embedded > 0) {
            log.info("Embedding processed: {} articles completed", embedded)
        }
        return embedded
    }

    fun clearRetriableErrors(maxRetries: Int = MAX_EMBED_RETRIES): Int {
        return articleRepository.clearRetriableEmbedErrors(maxRetries)
    }

    companion object {
        const val MAX_EMBED_RETRIES = 5
    }
}
