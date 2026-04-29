package com.blog.ai.scheduler

import com.blog.ai.crawl.application.CrawlService
import io.github.oshai.kotlinlogging.KotlinLogging
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class CrawlScheduler(
    private val crawlService: CrawlService,
) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Scheduled(cron = "0 0 9 * * MON", zone = "Asia/Seoul")
    @SchedulerLock(name = "crawl", lockAtMostFor = "PT1H", lockAtLeastFor = "PT1M")
    fun crawl() {
        log.info { "Scheduled crawl started" }
        crawlService.crawlAll()
    }
}
