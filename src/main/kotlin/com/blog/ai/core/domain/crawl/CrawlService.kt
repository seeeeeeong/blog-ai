package com.blog.ai.core.domain.crawl

import com.blog.ai.core.domain.blog.BlogCacheService
import com.blog.ai.storage.article.ArticleEntity
import com.blog.ai.storage.article.ArticleRepository
import com.blog.ai.storage.blog.BlogEntity
import com.blog.ai.storage.blog.BlogRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class CrawlService(
    private val blogRepository: BlogRepository,
    private val articleRepository: ArticleRepository,
    private val rssFeedParser: RssFeedParser,
    private val slackNotifier: SlackNotifier,
    private val blogCacheService: BlogCacheService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun crawlAll(): Int {
        val blogs = blogRepository.findAllByActiveTrue()
        var totalSaved = 0

        for (blog in blogs) {
            try {
                val saved = crawlBlog(blog)
                totalSaved += saved
            } catch (e: Exception) {
                log.error("크롤링 실패: blog={}, error={}", blog.name, e.message)
            }
        }

        if (totalSaved > 0) {
            slackNotifier.send("크롤링 완료: ${totalSaved}건 저장")
            blogCacheService.evictAll()
        }

        log.info("크롤링 완료: 총 {}건 저장", totalSaved)
        return totalSaved
    }

    private fun crawlBlog(blog: BlogEntity): Int {
        val articles = rssFeedParser.parse(blog.rssUrl)
        var saved = 0

        for (parsed in articles) {
            if (articleRepository.existsByUrlHash(parsed.urlHash)) continue

            articleRepository.save(
                ArticleEntity(
                    blog = blog,
                    title = parsed.title,
                    url = parsed.url,
                    urlHash = parsed.urlHash,
                    content = parsed.content,
                    publishedAt = parsed.publishedAt?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) },
                ),
            )
            saved++
        }

        if (saved > 0) {
            log.info("크롤링: blog={}, saved={}", blog.name, saved)
        }
        return saved
    }
}
