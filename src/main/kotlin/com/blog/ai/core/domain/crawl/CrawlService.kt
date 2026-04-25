package com.blog.ai.core.domain.crawl

import com.blog.ai.core.domain.blog.BlogCacheService
import com.blog.ai.storage.article.ArticleRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CrawlService(
    private val blogCacheService: BlogCacheService,
    private val rssFeedParser: RssFeedParser,
    private val articleSaveService: ArticleSaveService,
    private val articleRepository: ArticleRepository,
    private val webContentScraper: WebContentScraper,
) {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val DEFAULT_BACKFILL_BATCH_SIZE = 30
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
            blogCacheService.evictAll()
        }

        log.info { "Crawl completed: $totalSaved articles saved" }
        return totalSaved
    }

    @Transactional
    fun backfillMissingContent(batchSize: Int = DEFAULT_BACKFILL_BATCH_SIZE): Int {
        val articles = articleRepository.findWithoutContent(batchSize)
        var filled = 0

        for (article in articles) {
            val articleId = requireNotNull(article.id)
            val content = webContentScraper.scrape(article.url) ?: continue
            articleRepository.updateContent(articleId, content)
            articleRepository.resetEmbeddingForArticle(articleId)
            filled++
            log.debug { "Content backfilled: id=$articleId, title=${article.title}" }
        }

        if (filled > 0) {
            log.info { "Content backfill completed: $filled / ${articles.size} articles filled" }
        }
        return filled
    }
}
