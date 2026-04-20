package com.blog.ai.scheduler

import com.blog.ai.core.domain.crawl.ContentBackfillService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ContentBackfillScheduler(
    private val contentBackfillService: ContentBackfillService,
) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Scheduled(cron = "0 30 9 * * MON")
    fun backfill() {
        try {
            contentBackfillService.backfillMissingContent()
        } catch (e: Exception) {
            log.error(e) { "Content backfill failed" }
        }
    }
}
