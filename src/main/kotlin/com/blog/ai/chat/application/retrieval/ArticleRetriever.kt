package com.blog.ai.chat.application.retrieval

import com.blog.ai.chat.application.retrieval.QueryEmbedding
import com.blog.ai.chat.application.retrieval.QueryExpander
import com.blog.ai.chat.application.retrieval.RerankedExternalResult
import com.blog.ai.chat.domain.ChatAdvisorParams
import com.blog.ai.chat.infrastructure.rerank.RerankClient
import com.blog.ai.rag.application.RagSearchService
import com.blog.ai.rag.domain.RagChunkGranularity
import com.blog.ai.rag.domain.RagChunkHit
import com.blog.ai.rag.domain.RagSearchQuery
import com.blog.ai.rag.domain.RagSourceType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.document.Document
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.rag.Query
import org.springframework.ai.rag.retrieval.search.DocumentRetriever
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component("chatArticleRetriever")
class ArticleRetriever(
    private val embeddingModel: EmbeddingModel,
    private val ragSearchService: RagSearchService,
    private val chatQueryExpander: QueryExpander,
    private val chatRerankClient: RerankClient,
) : DocumentRetriever {
    companion object {
        private const val AUTHOR_CANDIDATE_POOL = 50
        private const val AUTHOR_TOP_K = 15
        private const val AUTHOR_GROUP_LIMIT = 3
        private const val AUTHOR_SIMILARITY_THRESHOLD = 0.55
        private const val EXTERNAL_CANDIDATE_POOL = 50
        private const val EXTERNAL_RERANK_INPUT = 30
        private const val EXTERNAL_FINAL_TOP_N = 5
        private const val SUPPLEMENTARY_FINAL_TOP_N = 3
        private const val CONTENT_SNIPPET_LENGTH = 1500
        private const val EXTERNAL_CANDIDATE_SIMILARITY = 0.3
        private const val EXTERNAL_RERANK_ELIGIBILITY = 0.4
        private const val EXTERNAL_RERANK_TOP_ABSTAIN = 0.5
        const val INTENT_PARAM = ChatAdvisorParams.INTENT
    }

    override fun retrieve(query: Query): List<Document> {
        val originalText = query.text().trim()
        if (originalText.isBlank()) return emptyList()

        val rewritten =
            (query.context()[ChatAdvisorParams.REWRITTEN_QUERY] as? String)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: originalText
        val intent = (query.context()[INTENT_PARAM] as? String) ?: "UNKNOWN"

        val variants = chatQueryExpander.expand(rewritten)
        val embeddings = variants.map { v -> QueryEmbedding(v, embed(v)) }

        val authorDocs = retrieveAuthorPosts(embeddings)
        if (authorDocs.isNotEmpty()) {
            val supplementary = retrieveExternalReranked(embeddings, rewritten, SUPPLEMENTARY_FINAL_TOP_N)
            val combined = authorDocs + supplementary.docs
            logRetrieval("author+supplementary", intent, rewritten, supplementary, authorDocs.size, combined)
            return combined
        }

        val external = retrieveExternalReranked(embeddings, rewritten, EXTERNAL_FINAL_TOP_N)
        val mode = if (external.docs.isEmpty()) "external-empty" else "external-only"
        logRetrieval(mode, intent, rewritten, external, 0, external.docs)
        return external.docs
    }

    private fun embed(text: String): String = embeddingModel.embed(text).joinToString(",", "[", "]")

    private fun retrieveAuthorPosts(queries: List<QueryEmbedding>): List<Document> {
        val unionHits =
            queries
                .flatMap { qe ->
                    ragSearchService
                        .search(
                            RagSearchQuery(
                                sourceType = RagSourceType.AUTHOR_POST,
                                granularity = RagChunkGranularity.CHUNK,
                                queryVector = qe.vector,
                                queryText = qe.text,
                                candidatePoolSize = AUTHOR_CANDIDATE_POOL,
                                limit = AUTHOR_TOP_K,
                            ),
                        ).filter { it.similarity >= AUTHOR_SIMILARITY_THRESHOLD }
                }.distinctBy { Triple(it.sourceType, it.sourceId, it.chunkIndex) }
        if (unionHits.isEmpty()) return emptyList()
        return buildDocuments(unionHits).take(AUTHOR_GROUP_LIMIT)
    }

    private fun retrieveExternalReranked(
        queries: List<QueryEmbedding>,
        rerankQuery: String,
        finalTopN: Int,
    ): RerankedExternalResult {
        val unionHits =
            queries
                .flatMap { qe ->
                    ragSearchService
                        .search(
                            RagSearchQuery(
                                sourceType = RagSourceType.EXTERNAL_ARTICLE,
                                granularity = RagChunkGranularity.CHUNK,
                                queryVector = qe.vector,
                                queryText = qe.text,
                                candidatePoolSize = EXTERNAL_CANDIDATE_POOL,
                                limit = EXTERNAL_CANDIDATE_POOL,
                            ),
                        ).filter { it.similarity >= EXTERNAL_CANDIDATE_SIMILARITY }
                }.distinctBy { Triple(it.sourceType, it.sourceId, it.chunkIndex) }
        if (unionHits.isEmpty()) return RerankedExternalResult.empty()
        val candidates = buildDocuments(unionHits).take(EXTERNAL_RERANK_INPUT)
        val reranked = chatRerankClient.rerank(rerankQuery, candidates, finalTopN)
        val topScore = reranked.firstOrNull()?.metadata?.get("rerankScore") as? Double
        if (topScore == null) {
            return RerankedExternalResult(emptyList(), null, abstained = true, rerankUnavailable = true)
        }
        if (topScore < EXTERNAL_RERANK_TOP_ABSTAIN) {
            return RerankedExternalResult(emptyList(), topScore, abstained = true)
        }
        val eligible = reranked.filter { passesRerankEligibility(it) }
        return RerankedExternalResult(eligible, topScore, abstained = false)
    }

    private fun passesRerankEligibility(doc: Document): Boolean {
        val score = doc.metadata["rerankScore"] as? Double ?: return true
        return score >= EXTERNAL_RERANK_ELIGIBILITY
    }

    private fun buildDocuments(hits: List<RagChunkHit>): List<Document> =
        hits
            .groupBy { it.sourceType to it.sourceId }
            .entries
            .sortedByDescending { (_, groupedHits) -> groupedHits.maxOf { it.score } }
            .map { (_, groupedHits) -> buildDocument(groupedHits) }

    private fun buildDocument(hits: List<RagChunkHit>): Document {
        val first = hits.first()
        val combinedContent =
            hits
                .sortedBy { it.chunkIndex }
                .joinToString("\n\n") { it.content.take(CONTENT_SNIPPET_LENGTH) }
        val source = first.sourceLabel()
        val sourceType = if (first.sourceType == RagSourceType.AUTHOR_POST) "author" else "external"

        return Document(
            "$source\n\n$combinedContent",
            mapOf(
                "sourceType" to sourceType,
                "title" to first.title,
                "url" to first.url.orEmpty(),
                "company" to (first.company ?: "me"),
                "similarity" to hits.maxOf { it.similarity },
                "score" to hits.maxOf { it.score },
            ),
        )
    }

    private fun RagChunkHit.sourceLabel(): String =
        when (sourceType) {
            RagSourceType.AUTHOR_POST -> {
                if (url.isNullOrBlank()) {
                    "Author post: $title"
                } else {
                    "Author post: [$title]($url)"
                }
            }

            RagSourceType.EXTERNAL_ARTICLE -> {
                "External source (NOT Author post): [$company - $title](${url.orEmpty()})"
            }
        }

    private fun logRetrieval(
        mode: String,
        intent: String,
        query: String,
        external: RerankedExternalResult,
        authorEligibleCount: Int,
        documents: List<Document>,
    ) {
        val labels =
            documents.joinToString(" | ") { d ->
                val type = d.metadata["sourceType"] as? String ?: "?"
                val t = d.metadata["title"] as? String ?: "?"
                val c = d.metadata["company"] as? String ?: "me"
                val rerank = d.metadata["rerankScore"] as? Double
                val sim = d.metadata["similarity"] as? Double
                val score = d.metadata["score"] as? Double
                val s = rerank ?: sim ?: score
                val tag = if (rerank != null) "r" else ""
                if (s != null) "$type:$c/$t($tag${"%.3f".format(s)})" else "$type:$c/$t"
            }
        val topScoreStr = external.topScore?.let { "%.3f".format(it) } ?: "n/a"
        log.info {
            "Chat retrieval mode=$mode intent=$intent query='${query.take(80)}' " +
                "topScore=$topScoreStr eligibleCount=${external.docs.size} " +
                "authorEligibleCount=$authorEligibleCount abstained=${external.abstained} " +
                "rerankUnavailable=${external.rerankUnavailable} [$labels]"
        }
    }
}
