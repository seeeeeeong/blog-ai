package com.blog.ai.core.domain.article

import com.blog.ai.core.support.jdbc.JdbcTimestamps
import com.blog.ai.storage.article.ArticleChunkRepository
import com.blog.ai.storage.article.ArticleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ArticleAdminService(
    private val articleRepository: ArticleRepository,
    private val articleChunkRepository: ArticleChunkRepository,
) {
    @Transactional(propagation = Propagation.REQUIRED)
    fun markAllForReembed(): Int {
        articleChunkRepository.truncateAll()
        return articleRepository.resetAllEmbeddingsForReprocess()
    }

    fun findArticlesForAdmin(
        limit: Int,
        offset: Int,
    ): List<ArticleAdmin> =
        articleRepository.findArticlesForAdmin(limit, offset).map { row ->
            ArticleAdmin(
                id = (row[0] as Number).toLong(),
                title = row[1] as String,
                url = row[2] as String,
                urlHash = row[3] as String,
                company = row[4] as String,
                embedded = row[5] as Boolean,
                embedError = row[6] as? String,
                crawledAt = requireNotNull(JdbcTimestamps.toOffsetDateTime(row[7])) { "crawled_at must not be null" },
            )
        }

    fun countTotal(): Long = articleRepository.count()

    fun countUnembedded(): Long = articleRepository.countUnembedded()

    fun countWithoutContent(): Long = articleRepository.countWithoutContent()
}
