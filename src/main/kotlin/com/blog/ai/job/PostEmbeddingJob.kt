package com.blog.ai.job

import com.blog.ai.post.application.embedding.PostEmbeddingService
import io.github.oshai.kotlinlogging.KotlinLogging
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PostEmbeddingJob(
    private val postEmbeddingService: PostEmbeddingService,
) {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val INTERVAL_MS = 300_000L
    }

    @Scheduled(fixedDelay = INTERVAL_MS)
    @SchedulerLock(name = "blogPostEmbedding", lockAtMostFor = "PT15M", lockAtLeastFor = "PT10S")
    fun embed() {
        try {
            val cleared = postEmbeddingService.clearRetriableErrors()
            if (cleared > 0) {
                log.info { "Post cleared $cleared retriable embed errors" }
            }

            val embedded = postEmbeddingService.embedPending()
            if (embedded > 0) {
                log.info { "Post embedding batch completed: $embedded posts" }
            }
        } catch (e: Exception) {
            log.error(e) { "Post embedding scheduler failed" }
        }
    }
}
