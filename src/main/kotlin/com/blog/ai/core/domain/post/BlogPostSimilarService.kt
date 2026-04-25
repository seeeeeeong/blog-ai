package com.blog.ai.core.domain.post

import com.blog.ai.storage.post.BlogPostRepository
import com.blog.ai.storage.rag.RagChunkGranularity
import com.blog.ai.storage.rag.RagChunkHit
import com.blog.ai.storage.rag.RagChunkRepository
import com.blog.ai.storage.rag.RagSearchQuery
import com.blog.ai.storage.rag.RagSourceType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class BlogPostSimilarService(
    private val blogPostRepository: BlogPostRepository,
    private val ragChunkRepository: RagChunkRepository,
) {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val DEFAULT_LIMIT = 10
        private const val CANDIDATE_POOL_SIZE = 50
        private const val CONTENT_SNIPPET_LENGTH = 300
        private const val LOGGED_TOP_ITEMS = 5
    }

    fun findSimilar(
        externalId: String,
        limit: Int = DEFAULT_LIMIT,
    ): SimilarResult {
        val startNanos = System.nanoTime()
        val result = findSimilarInternal(externalId, limit)
        val latencyMs = (System.nanoTime() - startNanos) / 1_000_000
        val topIds = result.items.take(LOGGED_TOP_ITEMS).map { it.id }
        log.info {
            "similar.query externalId=$externalId limit=$limit status=${result.status} " +
                "count=${result.items.size} topIds=$topIds latencyMs=$latencyMs"
        }
        return result
    }

    private fun findSimilarInternal(
        externalId: String,
        limit: Int,
    ): SimilarResult {
        val post = blogPostRepository.findByExternalId(externalId) ?: return SimilarResult.notFound()
        if (post.isDeleted) return SimilarResult.deleted()

        val postId = post.id ?: return SimilarResult.pending()
        val vector =
            ragChunkRepository.findDocumentVector(RagSourceType.AUTHOR_POST, postId)
                ?: return SimilarResult.pending()

        val queryText = buildQueryText(post.title, post.content)
        val hits =
            ragChunkRepository.searchHybrid(
                RagSearchQuery(
                    sourceType = RagSourceType.EXTERNAL_ARTICLE,
                    granularity = RagChunkGranularity.DOCUMENT,
                    queryVector = vector,
                    queryText = queryText,
                    candidatePoolSize = CANDIDATE_POOL_SIZE,
                    limit = limit,
                ),
            )

        return SimilarResult.ready(hits.map(::toSimilarArticle))
    }

    private fun buildQueryText(
        title: String,
        content: String?,
    ): String {
        val contentSnippet = content?.take(CONTENT_SNIPPET_LENGTH).orEmpty()
        return "$title $contentSnippet".trim()
    }

    private fun toSimilarArticle(hit: RagChunkHit): SimilarArticle =
        SimilarArticle(
            id = hit.sourceId,
            title = hit.title,
            url = hit.url.orEmpty(),
            company = hit.company.orEmpty(),
            score = hit.score,
        )
}
