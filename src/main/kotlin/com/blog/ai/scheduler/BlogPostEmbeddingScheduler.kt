package com.blog.ai.scheduler

import com.blog.ai.core.domain.post.BlogPostEmbedService
import io.github.oshai.kotlinlogging.KotlinLogging
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class BlogPostEmbeddingScheduler(
    private val blogPostEmbedService: BlogPostEmbedService,
) {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val INTERVAL_MS = 300_000L
    }

    @Scheduled(fixedDelay = INTERVAL_MS)
    @SchedulerLock(name = "blogPostEmbedding", lockAtMostFor = "PT15M", lockAtLeastFor = "PT10S")
    fun embed() {
        try {
            val cleared = blogPostEmbedService.clearRetriableErrors()
            if (cleared > 0) {
                log.info { "BlogPost cleared $cleared retriable embed errors" }
            }

            val embedded = blogPostEmbedService.embedPending()
            if (embedded > 0) {
                log.info { "BlogPost embedding batch completed: $embedded posts" }
            }
        } catch (e: Exception) {
            log.error(e) { "BlogPost embedding scheduler failed" }
        }
    }
}
