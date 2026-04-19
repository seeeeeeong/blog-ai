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
        private const val CONTENT_SNIPPET_LENGTH = 300
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

    fun diagnose(
        externalId: String,
        limit: Int = DEFAULT_LIMIT,
    ): SimilarDiagnoseResult {
        val post = blogPostRepository.findByExternalId(externalId) ?: return SimilarDiagnoseResult.notFound()
        if (post.isDeleted) return SimilarDiagnoseResult.deleted()

        val vector = blogPostRepository.findEmbeddingText(externalId) ?: return SimilarDiagnoseResult.pending()

        val queryText = buildQueryText(post.title, post.content)
        val vectorOnly = articleRepository.findSimilarByVector(vector, limit).map(::toSimilarArticle)
        val bm25Only = articleRepository.findSimilarByBm25(queryText, limit).map(::toSimilarArticle)
        val hybridRows = articleRepository.findSimilarHybrid(vector, queryText, CANDIDATE_POOL_SIZE, limit)
        val hybrid = hybridRows.map(::toSimilarArticle)

        return SimilarDiagnoseResult.ready(vectorOnly, bm25Only, hybrid)
    }

    private fun buildQueryText(
        title: String,
        content: String?,
    ): String {
        val contentSnippet = content?.take(CONTENT_SNIPPET_LENGTH).orEmpty()
        return "$title $contentSnippet".trim()
    }

    private fun toSimilarArticle(row: Array<Any>): SimilarArticle =
        SimilarArticle(
            id = (row[0] as Number).toLong(),
            title = row[1] as String,
            url = row[2] as String,
            company = row[3] as String,
            score = (row[4] as Number).toDouble(),
        )
}
