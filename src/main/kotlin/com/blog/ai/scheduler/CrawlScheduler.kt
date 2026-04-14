package com.blog.ai.scheduler

import com.blog.ai.core.domain.article.ArticleEmbedService
import com.blog.ai.core.domain.crawl.CrawlService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class CrawlScheduler(
    private val crawlService: CrawlService,
    private val articleEmbedService: ArticleEmbedService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 */3 * * *")
    fun crawlAndEmbed() {
        log.info("스케줄 크롤링 시작")
        val saved = crawlService.crawlAll()
        if (saved > 0) {
            articleEmbedService.embedPending()
        }
    }
}
