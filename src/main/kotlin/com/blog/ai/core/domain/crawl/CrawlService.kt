package com.blog.ai.core.domain.crawl

import com.blog.ai.core.domain.blog.BlogCacheService
import com.blog.ai.storage.blog.BlogRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CrawlService(
    private val blogRepository: BlogRepository,
    private val rssFeedParser: RssFeedParser,
    private val articleSaveService: ArticleSaveService,
    private val slackNotifier: SlackNotifier,
    private val blogCacheService: BlogCacheService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun crawlAll(): Int {
        val blogs = blogRepository.findAllByActiveTrue()
        var totalSaved = 0

        for (blog in blogs) {
            try {
                val parsed = rssFeedParser.parse(blog.rssUrl)
                val saved = articleSaveService.saveNewArticles(blog, parsed)
                totalSaved += saved
            } catch (e: Exception) {
                log.error("Crawl failed: blog={}, error={}", blog.name, e.message)
            }
        }

        if (totalSaved > 0) {
            slackNotifier.send("Crawl completed: $totalSaved articles saved")
            blogCacheService.evictAll()
        }

        log.info("Crawl completed: {} articles saved", totalSaved)
        return totalSaved
    }
}
