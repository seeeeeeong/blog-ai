package com.blog.ai.core.domain.article

import com.blog.ai.storage.article.ArticleChunkRepository
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ArticleChunkService(
    private val articleChunkRepository: ArticleChunkRepository,
    private val vectorStore: VectorStore,
) {

    @Transactional
    fun saveChunks(articleId: Long, title: String, content: String) {
        articleChunkRepository.deleteByArticleId(articleId)

        val chunks = splitContent(content)
        chunks.forEachIndexed { index, chunk ->
            val docContent = "제목: $title\n\n$chunk"
            val doc = Document(docContent, mapOf("articleId" to articleId, "chunkIndex" to index))
            vectorStore.add(listOf(doc))

            articleChunkRepository.saveChunk(articleId, index, chunk)
        }
    }

    private fun splitContent(content: String, maxSize: Int = 1000): List<String> {
        if (content.length <= maxSize) return listOf(content)

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < content.length) {
            val end = (start + maxSize).coerceAtMost(content.length)
            chunks.add(content.substring(start, end))
            start = end
        }
        return chunks
    }
}
