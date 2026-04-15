package com.blog.ai.scheduler

import com.blog.ai.core.domain.article.ArticleEmbedService
import com.blog.ai.core.domain.crawl.CrawlService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class CrawlScheduler(
    private val crawlService: CrawlService,
    private val articleEmbedService: ArticleEmbedService,
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Scheduled(cron = "0 0 */3 * * *")
    fun crawlAndEmbed() {
        log.info { "Scheduled crawl started" }
        val saved = crawlService.crawlAll()
        if (saved > 0) {
            articleEmbedService.embedPending()
        }
    }
}
