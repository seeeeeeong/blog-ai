package com.blog.ai.core.domain.article

import com.blog.ai.core.support.text.TextSplitter
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
    @Transactional
    fun saveChunks(
        metadata: ChunkMetadata,
        content: String,
    ) {
        articleChunkRepository.deleteByArticleId(metadata.articleId)

        val chunks = TextSplitter.split(content)
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
                ),
            )
        }
    }
}
