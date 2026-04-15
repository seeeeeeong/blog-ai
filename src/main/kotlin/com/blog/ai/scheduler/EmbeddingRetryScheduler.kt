package com.blog.ai.scheduler

import com.blog.ai.core.domain.article.ArticleEmbedService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class EmbeddingRetryScheduler(
    private val articleEmbedService: ArticleEmbedService,
) {

    companion object {
        private val log = KotlinLogging.logger {}
        private const val RETRY_INTERVAL_MS = 3_600_000L
    }

    @Scheduled(fixedDelay = RETRY_INTERVAL_MS)
    fun retryFailed() {
        val cleared = articleEmbedService.clearRetriableErrors()
        if (cleared > 0) {
            log.info { "Embedding errors cleared: $cleared articles, retry started (excluding over ${ArticleEmbedService.MAX_EMBED_RETRIES} retries)" }
            articleEmbedService.embedPending()
        }
    }
}
