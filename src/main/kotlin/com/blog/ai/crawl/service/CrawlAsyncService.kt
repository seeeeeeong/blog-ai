package com.blog.ai.crawl.service

import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class CrawlAsyncService(
    private val crawlService: CrawlService,
) {
    @Async
    fun crawlAsync() {
        crawlService.crawlAll()
    }
}
