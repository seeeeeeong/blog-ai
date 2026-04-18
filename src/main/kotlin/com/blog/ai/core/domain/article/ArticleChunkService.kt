package com.blog.ai.core.domain.article

import com.blog.ai.storage.article.ArticleChunkRepository
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ArticleChunkService(
    private val articleChunkRepository: ArticleChunkRepository,
    private val vectorStore: VectorStore,
) {
    companion object {
        private const val CHUNK_SIZE = 1200
        private const val CHUNK_OVERLAP = 150
        private val SEPARATORS = listOf("\n\n", "\n", ". ", " ")
    }

    @Transactional
    fun saveChunks(
        metadata: ChunkMetadata,
        content: String,
    ) {
        articleChunkRepository.deleteByArticleId(metadata.articleId)

        val chunks = splitRecursive(content)
        val documents =
            chunks.mapIndexed { index, chunk ->
                val docContent = "title: ${metadata.title}\ncompany: ${metadata.company}\n\n$chunk"
                Document(
                    docContent,
                    mapOf(
                        "articleId" to metadata.articleId,
                        "chunkIndex" to index,
                        "title" to metadata.title,
                        "company" to metadata.company,
                        "url" to metadata.url,
                        "publishedAt" to (metadata.publishedAt?.toString() ?: ""),
                    ),
                )
            }

        if (documents.isNotEmpty()) {
            vectorStore.add(documents)
        }

        chunks.forEachIndexed { index, chunk ->
            articleChunkRepository.saveChunk(
                SaveChunkCommand(
                    articleId = metadata.articleId,
                    chunkIndex = index,
                    content = chunk,
                    embedding = "",
                ),
            )
        }
    }

    private fun splitRecursive(text: String): List<String> {
        if (text.length <= CHUNK_SIZE) return listOf(text)
        return doSplit(text, SEPARATORS)
    }

    private fun doSplit(
        text: String,
        separators: List<String>,
    ): List<String> {
        if (text.length <= CHUNK_SIZE) return listOf(text)

        val separator = separators.firstOrNull { text.contains(it) } ?: return splitWithOverlap(text)
        val parts = text.split(separator)
        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        for (part in parts) {
            val candidate = if (current.isEmpty()) part else "${current}${separator}$part"

            if (candidate.length > CHUNK_SIZE && current.isNotEmpty()) {
                chunks.add(current.toString().trim())
                current.clear()
                current.append(part)
            } else {
                current.clear()
                current.append(candidate)
            }
        }

        if (current.isNotBlank()) {
            chunks.add(current.toString().trim())
        }

        return chunks
            .flatMap { chunk ->
                if (chunk.length > CHUNK_SIZE && separators.size > 1) {
                    doSplit(chunk, separators.drop(1))
                } else {
                    listOf(chunk)
                }
            }.let { addOverlap(it) }
    }

    private fun splitWithOverlap(text: String): List<String> {
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = (start + CHUNK_SIZE).coerceAtMost(text.length)
            chunks.add(text.substring(start, end))
            start = end - CHUNK_OVERLAP
            if (start >= text.length - CHUNK_OVERLAP) break
        }
        return chunks
    }

    private fun addOverlap(chunks: List<String>): List<String> {
        if (chunks.size <= 1) return chunks

        return chunks.mapIndexed { index, chunk ->
            if (index == 0) return@mapIndexed chunk

            val prevChunk = chunks[index - 1]
            val overlapText = prevChunk.takeLast(CHUNK_OVERLAP)
            "$overlapText$chunk"
        }
    }
}
