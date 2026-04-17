package com.blog.ai.core.domain.crawl

import com.blog.ai.core.domain.article.ArticleEmbedService
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class CrawlAsyncService(
    private val crawlService: CrawlService,
    private val articleEmbedService: ArticleEmbedService,
) {

    @Async
    fun crawlAndEmbed() {
        crawlService.crawlAll()
        articleEmbedService.embedPending()
    }
}
