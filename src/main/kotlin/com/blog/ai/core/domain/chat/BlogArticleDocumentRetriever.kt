package com.blog.ai.core.domain.chat

import com.blog.ai.storage.article.ArticleRepository
import com.blog.ai.storage.post.BlogPostChunkHit
import com.blog.ai.storage.post.BlogPostChunkRepository
import com.blog.ai.storage.post.BlogPostRepository
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
    private val blogPostChunkRepository: BlogPostChunkRepository,
    private val blogPostRepository: BlogPostRepository,
    private val chatQueryRewriter: ChatQueryRewriter,
    private val vectorStore: VectorStore,
) : DocumentRetriever {
    companion object {
        private const val MY_POST_TOP_K = 8
        private const val MY_POST_GROUP_LIMIT = 3
        private const val MY_POST_SIMILARITY_THRESHOLD = 0.5
        private const val EXTERNAL_CHUNK_TOP_K = 8
        private const val EXTERNAL_ARTICLE_TOP_K = 5
        private const val SUPPLEMENTARY_ARTICLE_TOP_K = 3
        private const val CANDIDATE_POOL_SIZE = 50
        private const val CONTENT_SNIPPET_LENGTH = 1500
        private const val MIN_SCORE_THRESHOLD = 0.005
        private const val EXTERNAL_CHUNK_SIMILARITY_THRESHOLD = 0.2
    }

    override fun retrieve(query: Query): List<Document> {
        val originalText = query.text().trim()
        if (originalText.isBlank()) return emptyList()

        val sessionId = query.context()["chat_memory_conversation_id"] as? String
        val text =
            if (sessionId != null) {
                chatQueryRewriter.rewrite(sessionId, originalText)
            } else {
                originalText
            }

        val myPostDocs = retrieveFromMyPosts(text)
        if (myPostDocs.isNotEmpty()) {
            val supplementary = retrieveSupplementaryArticles(text, SUPPLEMENTARY_ARTICLE_TOP_K)
            val combined = myPostDocs + supplementary
            logRetrieval("author+supplementary", text, combined)
            return combined
        }

        val externalChunkDocs = retrieveFromChunks(text)
        if (externalChunkDocs.isNotEmpty()) {
            logRetrieval("external-chunks", text, externalChunkDocs)
            return externalChunkDocs
        }

        val articleDocs = retrieveFromArticles(text, EXTERNAL_ARTICLE_TOP_K)
        logRetrieval("external-articles", text, articleDocs)
        return articleDocs
    }

    private fun retrieveFromMyPosts(text: String): List<Document> {
        val vector = embeddingModel.embed(text).joinToString(",", "[", "]")
        val hits =
            blogPostChunkRepository.findSimilarChunks(
                queryVector = vector,
                topK = MY_POST_TOP_K,
                similarityThreshold = MY_POST_SIMILARITY_THRESHOLD,
            )
        if (hits.isEmpty()) return emptyList()

        return hits
            .groupBy { it.postId }
            .entries
            .sortedByDescending { (_, chunks) -> chunks.maxOf { it.similarity } }
            .take(MY_POST_GROUP_LIMIT)
            .mapNotNull { (postId, chunks) -> buildMyPostDocument(postId, chunks) }
    }

    private fun buildMyPostDocument(
        postId: Long,
        chunks: List<BlogPostChunkHit>,
    ): Document? {
        val post = blogPostRepository.findById(postId).orElse(null) ?: return null
        val title = post.title
        val url = post.url ?: return null
        val combinedContent =
            chunks
                .sortedBy { it.chunkIndex }
                .joinToString("\n\n") { it.content.take(CONTENT_SNIPPET_LENGTH) }
        val source = "Author post: [$title]($url)"

        return Document(
            "$source\n\n$combinedContent",
            mapOf(
                "sourceType" to "author",
                "title" to title,
                "url" to url,
                "similarity" to chunks.maxOf { it.similarity },
            ),
        )
    }

    private fun retrieveSupplementaryArticles(
        text: String,
        topK: Int,
    ): List<Document> = retrieveFromArticles(text, topK)

    private fun retrieveFromChunks(text: String): List<Document> {
        val searchRequest =
            SearchRequest
                .builder()
                .query(text)
                .topK(EXTERNAL_CHUNK_TOP_K)
                .similarityThreshold(EXTERNAL_CHUNK_SIMILARITY_THRESHOLD)
                .build()

        val chunks = vectorStore.similaritySearch(searchRequest) ?: return emptyList()
        return chunks
            .groupBy { it.metadata["articleId"] }
            .entries
            .take(EXTERNAL_ARTICLE_TOP_K)
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
                        "sourceType" to "external",
                        "title" to title,
                        "company" to company,
                        "url" to url,
                    ),
                )
            }
    }

    private fun retrieveFromArticles(
        text: String,
        topK: Int,
    ): List<Document> {
        val vector = embeddingModel.embed(text).joinToString(",", "[", "]")
        val rows = articleRepository.findHybridForChat(vector, text, CANDIDATE_POOL_SIZE, topK)
        return rows
            .map(::toArticleDocument)
            .filter { (it.metadata["score"] as Double) >= MIN_SCORE_THRESHOLD }
            .filter { (it.metadata["hasContent"] as Boolean) }
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
                "sourceType" to "external",
                "id" to id,
                "title" to title,
                "url" to url,
                "company" to company,
                "score" to score,
                "hasContent" to snippet.isNotBlank(),
            )
        return Document(body, metadata)
    }

    private fun logRetrieval(
        mode: String,
        text: String,
        documents: List<Document>,
    ) {
        val labels =
            documents.joinToString(" | ") { d ->
                val type = d.metadata["sourceType"] as? String ?: "?"
                val t = d.metadata["title"] as? String ?: "?"
                val c = d.metadata["company"] as? String ?: "me"
                val sim = d.metadata["similarity"] as? Double
                val score = d.metadata["score"] as? Double
                val s = sim ?: score
                if (s != null) "$type:$c/$t(${"%.3f".format(s)})" else "$type:$c/$t"
            }
        log.info { "Chat retrieval mode=$mode query='${text.take(80)}' hits=${documents.size} [$labels]" }
    }
}
