package com.blog.ai.core.domain.crawl

import com.blog.ai.core.domain.blog.BlogCacheService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

@Service
class CrawlService(
    private val blogCacheService: BlogCacheService,
    private val rssFeedParser: RssFeedParser,
    private val articleSaveService: ArticleSaveService,
    private val slackNotifier: SlackNotifier,
) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    fun crawlAll(): Int {
        val blogs = blogCacheService.getActiveBlogs()
        var totalSaved = 0

        for (blog in blogs) {
            try {
                val parsed = rssFeedParser.parse(blog.rssUrl)
                val saved = articleSaveService.saveNewArticles(blog.id, parsed)
                totalSaved += saved
            } catch (e: Exception) {
                log.error(e) { "Crawl failed: blog=${blog.name}" }
            }
        }

        if (totalSaved > 0) {
            slackNotifier.send("Crawl completed: $totalSaved articles saved")
            blogCacheService.evictAll()
        }

        log.info { "Crawl completed: $totalSaved articles saved" }
        return totalSaved
    }
}
