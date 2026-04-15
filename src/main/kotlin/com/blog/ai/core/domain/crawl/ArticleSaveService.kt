package com.blog.ai.core.domain.crawl

import com.blog.ai.storage.article.ArticleEntity
import com.blog.ai.storage.article.ArticleRepository
import com.blog.ai.storage.blog.BlogEntity
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class ArticleSaveService(
    private val articleRepository: ArticleRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun saveNewArticles(blog: BlogEntity, parsed: List<ParsedArticle>): Int {
        var saved = 0

        for (article in parsed) {
            if (articleRepository.existsByUrlHash(article.urlHash)) continue

            articleRepository.save(
                ArticleEntity(
                    blog = blog,
                    title = article.title,
                    url = article.url,
                    urlHash = article.urlHash,
                    content = article.content,
                    publishedAt = article.publishedAt?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) },
                ),
            )
            saved++
        }

        if (saved > 0) {
            log.info("Crawl: blog={}, saved={}", blog.name, saved)
        }
        return saved
    }
}
