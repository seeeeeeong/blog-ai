package com.blog.ai.post.application.similar

import com.blog.ai.post.domain.SimilarArticle
import com.blog.ai.post.domain.SimilarResult
import com.blog.ai.post.infrastructure.PostRepository
import com.blog.ai.rag.application.RagSearchService
import com.blog.ai.rag.domain.RagChunkGranularity
import com.blog.ai.rag.domain.RagSearchQuery
import com.blog.ai.rag.domain.RagSourceType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class SimilarPostService(
    private val postRepository: PostRepository,
    private val ragSearchService: RagSearchService,
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
        val post = postRepository.findByExternalId(externalId) ?: return SimilarResult.notFound()
        if (post.isDeleted) return SimilarResult.deleted()

        val postId = post.id ?: return SimilarResult.pending()
        val vector =
            ragSearchService.findDocumentVector(RagSourceType.AUTHOR_POST, postId)
                ?: return SimilarResult.pending()

        val queryText = buildQueryText(post.title, post.content)
        val hits =
            ragSearchService.search(
                RagSearchQuery(
                    sourceType = RagSourceType.EXTERNAL_ARTICLE,
                    granularity = RagChunkGranularity.DOCUMENT,
                    queryVector = vector,
                    queryText = queryText,
                    candidatePoolSize = CANDIDATE_POOL_SIZE,
                    limit = limit,
                ),
            )

        return SimilarResult.ready(hits.map(SimilarArticle::of))
    }

    private fun buildQueryText(
        title: String,
        content: String?,
    ): String {
        val contentSnippet = content?.take(CONTENT_SNIPPET_LENGTH).orEmpty()
        return "$title $contentSnippet".trim()
    }
}
