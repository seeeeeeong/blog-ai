package com.blog.ai.scheduler

import com.blog.ai.article.ArticleEmbeddingService
import io.github.oshai.kotlinlogging.KotlinLogging
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class EmbeddingScheduler(
    private val articleEmbeddingService: ArticleEmbeddingService,
) {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val INTERVAL_MS = 1_800_000L
    }

    @Scheduled(fixedDelay = INTERVAL_MS)
    @SchedulerLock(name = "articleEmbedding", lockAtMostFor = "PT25M", lockAtLeastFor = "PT30S")
    fun embed() {
        val cleared = articleEmbeddingService.clearRetriableErrors()
        if (cleared > 0) {
            log.info { "Cleared $cleared retriable embed errors" }
        }

        val embedded = articleEmbeddingService.embedPending()
        if (embedded > 0) {
            log.info { "Embedding batch completed: $embedded articles" }
        }
    }
}
