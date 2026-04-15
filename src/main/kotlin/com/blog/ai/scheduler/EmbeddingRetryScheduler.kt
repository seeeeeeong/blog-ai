package com.blog.ai.scheduler

import com.blog.ai.core.domain.article.ArticleEmbedService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class EmbeddingRetryScheduler(
    private val articleEmbedService: ArticleEmbedService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 3600000)
    fun retryFailed() {
        val cleared = articleEmbedService.clearRetriableErrors()
        if (cleared > 0) {
            log.info("Embedding errors cleared: {} articles, retry started (excluding over {} retries)", cleared, ArticleEmbedService.MAX_EMBED_RETRIES)
            articleEmbedService.embedPending()
        }
    }
}
