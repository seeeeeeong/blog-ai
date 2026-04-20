package com.blog.ai.core.domain.chat

import com.blog.ai.storage.article.ArticleRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.document.Document
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.rag.Query
import org.springframework.ai.rag.retrieval.search.DocumentRetriever
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class BlogArticleDocumentRetriever(
    private val embeddingModel: EmbeddingModel,
    private val articleRepository: ArticleRepository,
    private val chatQueryRewriter: ChatQueryRewriter,
    private val vectorStore: VectorStore,
) : DocumentRetriever {
    companion object {
        private const val CHUNK_TOP_K = 8
        private const val ARTICLE_TOP_K = 5
        private const val CANDIDATE_POOL_SIZE = 50
        private const val CONTENT_SNIPPET_LENGTH = 1500
        private const val MIN_SCORE_THRESHOLD = 0.005
        private const val CHUNK_SIMILARITY_THRESHOLD = 0.3
    }

    override fun retrieve(query: Query): List<Document> {
        val originalText = query.text().trim()
        if (originalText.isBlank()) return emptyList()

        val sessionId =
            query.context()["chat_memory_conversation_id"] as? String
        val text =
            if (sessionId != null) {
                chatQueryRewriter.rewrite(sessionId, originalText)
            } else {
                originalText
            }

        val chunkDocs = retrieveFromChunks(text)
        if (chunkDocs.isNotEmpty()) {
            log.info {
                val titles =
                    chunkDocs.joinToString(" | ") { d ->
                        val t = d.metadata["title"] as? String ?: "?"
                        val c = d.metadata["company"] as? String ?: "?"
                        "$c/$t"
                    }
                "Chat chunk retrieval: query='${text.take(80)}' hits=${chunkDocs.size} [$titles]"
            }
            return chunkDocs
        }

        return retrieveFromArticles(text)
    }

    private fun retrieveFromChunks(text: String): List<Document> {
        val searchRequest =
            SearchRequest
                .builder()
                .query(text)
                .topK(CHUNK_TOP_K)
                .similarityThreshold(CHUNK_SIMILARITY_THRESHOLD)
                .build()

        val chunks = vectorStore.similaritySearch(searchRequest) ?: return emptyList()

        return chunks
            .groupBy { it.metadata["articleId"] }
            .entries
            .take(ARTICLE_TOP_K)
            .map { (_, articleChunks) ->
                val first = articleChunks.first()
                val title = first.metadata["title"] as? String ?: ""
                val company = first.metadata["company"] as? String ?: ""
                val url = first.metadata["url"] as? String ?: ""
                val combinedContent =
                    articleChunks.joinToString("\n\n") { it.text?.take(CONTENT_SNIPPET_LENGTH) ?: "" }
                val source = "Source: [$company - $title]($url)"

                Document(
                    "$source\n\n$combinedContent",
                    mapOf(
                        "title" to title,
                        "company" to company,
                        "url" to url,
                    ),
                )
            }
    }

    private fun retrieveFromArticles(text: String): List<Document> {
        val vector = embeddingModel.embed(text).joinToString(",", "[", "]")
        val rows = articleRepository.findHybridForChat(vector, text, CANDIDATE_POOL_SIZE, ARTICLE_TOP_K)
        val documents =
            rows
                .map(::toArticleDocument)
                .filter { (it.metadata["score"] as Double) >= MIN_SCORE_THRESHOLD }
                .filter { (it.metadata["hasContent"] as Boolean) }

        log.info {
            val titles =
                documents.joinToString(" | ") { d ->
                    val t = d.metadata["title"] as? String ?: "?"
                    val c = d.metadata["company"] as? String ?: "?"
                    "$c/$t"
                }
            "Chat article retrieval: query='${text.take(80)}' hits=${documents.size} [$titles]"
        }

        return documents
    }

    private fun toArticleDocument(row: Array<Any>): Document {
        val id = (row[0] as Number).toLong()
        val title = row[1] as String
        val url = row[2] as String
        val company = row[3] as String
        val content = (row[4] as String?).orEmpty()
        val score = (row[5] as Number).toDouble()

        val snippet = content.take(CONTENT_SNIPPET_LENGTH)
        val source = "Source: [$company - $title]($url)"
        val body = if (snippet.isBlank()) source else "$source\n\n$snippet"

        val metadata =
            mapOf(
                "id" to id,
                "title" to title,
                "url" to url,
                "company" to company,
                "score" to score,
                "hasContent" to snippet.isNotBlank(),
            )
        return Document(body, metadata)
    }
}
