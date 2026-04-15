package com.blog.ai.core.domain.crawl

import com.blog.ai.storage.article.ArticleEntity
import com.blog.ai.storage.article.ArticleRepository
import com.blog.ai.storage.blog.BlogEntity
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class ArticleSaveService(
    private val articleRepository: ArticleRepository,
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Transactional
    fun saveNewArticles(blog: BlogEntity, parsed: List<ParsedArticle>): Int {
        var saved = 0

        for (article in parsed) {
            if (articleRepository.existsByUrlHash(article.urlHash)) continue

            articleRepository.save(
                ArticleEntity.create(
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
            log.info { "Crawl: blog=${blog.name}, saved=$saved" }
        }
        return saved
    }
}
