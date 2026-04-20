package com.blog.ai.core.domain.crawl

import com.blog.ai.storage.article.ArticleRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ContentBackfillService(
    private val articleRepository: ArticleRepository,
    private val webContentScraper: WebContentScraper,
) {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val DEFAULT_BATCH_SIZE = 30
    }

    @Transactional
    fun backfillMissingContent(batchSize: Int = DEFAULT_BATCH_SIZE): Int {
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
