package com.blog.ai.core.domain.chat

import com.blog.ai.storage.article.ArticleRepository
import org.springframework.ai.document.Document
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.rag.Query
import org.springframework.ai.rag.retrieval.search.DocumentRetriever
import org.springframework.stereotype.Component

@Component
class BlogArticleDocumentRetriever(
    private val embeddingModel: EmbeddingModel,
    private val articleRepository: ArticleRepository,
) : DocumentRetriever {
    companion object {
        private const val TOP_K = 5
        private const val CANDIDATE_POOL_SIZE = 50
        private const val CONTENT_SNIPPET_LENGTH = 1500
    }

    override fun retrieve(query: Query): List<Document> {
        val text = query.text().trim()
        if (text.isBlank()) return emptyList()

        val vector = embeddingModel.embed(text).joinToString(",", "[", "]")
        val rows = articleRepository.findHybridForChat(vector, text, CANDIDATE_POOL_SIZE, TOP_K)
        return rows.map(::toDocument)
    }

    private fun toDocument(row: Array<Any>): Document {
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
            )
        return Document(body, metadata)
    }
}
