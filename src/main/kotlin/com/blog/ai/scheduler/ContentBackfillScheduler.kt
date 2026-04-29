package com.blog.ai.scheduler

import com.blog.ai.crawl.application.CrawlService
import io.github.oshai.kotlinlogging.KotlinLogging
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ContentBackfillScheduler(
    private val crawlService: CrawlService,
) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Scheduled(cron = "0 30 9 * * MON", zone = "Asia/Seoul")
    @SchedulerLock(name = "contentBackfill", lockAtMostFor = "PT1H", lockAtLeastFor = "PT1M")
    fun backfill() {
        try {
            crawlService.backfillMissingContent()
        } catch (e: Exception) {
            log.error(e) { "Content backfill failed" }
        }
    }
}
