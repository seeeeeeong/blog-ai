package com.blog.ai.crawl.application

import com.blog.ai.article.infrastructure.ArticleEntity
import com.blog.ai.article.infrastructure.ArticleRepository
import com.blog.ai.blog.infrastructure.BlogRepository
import com.blog.ai.crawl.domain.ParsedArticle
import com.blog.ai.global.error.AppException
import com.blog.ai.global.error.ErrorCode
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class ArticleSaveService(
    private val articleRepository: ArticleRepository,
    private val blogRepository: BlogRepository,
) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Transactional
    fun saveNewArticles(
        blogId: Long,
        parsed: List<ParsedArticle>,
    ): Int {
        val blog =
            blogRepository
                .findById(blogId)
                .orElseThrow { AppException(ErrorCode.BLOG_NOT_FOUND) }

        var saved = 0

        for (article in parsed) {
            val alreadyExists = articleRepository.existsByUrlHash(article.urlHash)
            if (alreadyExists) continue

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
