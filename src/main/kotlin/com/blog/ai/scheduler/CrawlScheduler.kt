package com.blog.ai.scheduler

import com.blog.ai.core.domain.crawl.CrawlService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class CrawlScheduler(
    private val crawlService: CrawlService,
) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Scheduled(cron = "0 0 9 * * MON")
    fun crawl() {
        log.info { "Scheduled crawl started" }
        crawlService.crawlAll()
    }
}
