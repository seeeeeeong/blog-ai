package com.blog.ai.core.domain.post

import com.blog.ai.storage.article.ArticleRepository
import com.blog.ai.storage.post.BlogPostRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class BlogPostSimilarService(
    private val blogPostRepository: BlogPostRepository,
    private val articleRepository: ArticleRepository,
) {
    companion object {
        private const val DEFAULT_LIMIT = 10
        private const val CANDIDATE_POOL_SIZE = 50
    }

    fun findSimilar(
        externalId: String,
        limit: Int = DEFAULT_LIMIT,
    ): SimilarResult {
        val post = blogPostRepository.findByExternalId(externalId) ?: return SimilarResult.notFound()
        if (post.isDeleted) return SimilarResult.deleted()

        val vector = blogPostRepository.findEmbeddingText(externalId) ?: return SimilarResult.pending()

        val queryText = buildQueryText(post.title, post.content)
        val rows = articleRepository.findSimilarHybrid(vector, queryText, CANDIDATE_POOL_SIZE, limit)

        return SimilarResult.ready(rows.map(::toSimilarArticle))
    }

    private fun buildQueryText(
        title: String,
        content: String?,
    ): String = "$title ${content ?: ""}".trim()

    private fun toSimilarArticle(row: Array<Any>): SimilarArticle =
        SimilarArticle(
            id = (row[0] as Number).toLong(),
            title = row[1] as String,
            url = row[2] as String,
            company = row[3] as String,
            score = (row[4] as Number).toDouble(),
        )
}
