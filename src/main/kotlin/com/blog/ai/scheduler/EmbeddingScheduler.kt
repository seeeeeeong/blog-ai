package com.blog.ai.scheduler

import com.blog.ai.core.domain.article.ArticleEmbedService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class EmbeddingScheduler(
    private val articleEmbedService: ArticleEmbedService,
) {

    companion object {
        private val log = KotlinLogging.logger {}
        private const val INTERVAL_MS = 1_800_000L
    }

    @Scheduled(fixedDelay = INTERVAL_MS)
    fun embed() {
        val cleared = articleEmbedService.clearRetriableErrors()
        if (cleared > 0) {
            log.info { "Cleared $cleared retriable embed errors" }
        }

        val embedded = articleEmbedService.embedPending()
        if (embedded > 0) {
            log.info { "Embedding batch completed: $embedded articles" }
        }
    }
}
