package com.blog.ai.rag.application

import com.blog.ai.rag.domain.ChunkEmbedding
import com.blog.ai.rag.domain.RagChunkGranularity
import com.blog.ai.rag.domain.RagChunkWrite
import com.blog.ai.rag.domain.RagSourceType
import com.blog.ai.rag.infrastructure.RagChunkRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RagWriteService(
    private val ragChunkRepository: RagChunkRepository,
) {
    @Transactional
    fun replaceAuthorPost(
        postId: Long,
        title: String,
        url: String?,
        content: String,
        docVector: String,
        chunks: List<ChunkEmbedding>,
    ) {
        val commands =
            buildList {
                if (content.isNotBlank()) {
                    add(
                        RagChunkWrite(
                            sourceType = RagSourceType.AUTHOR_POST,
                            sourceId = postId,
                            granularity = RagChunkGranularity.DOCUMENT,
                            chunkIndex = DOCUMENT_CHUNK_INDEX,
                            title = title,
                            url = url,
                            company = null,
                            content = content,
                            embedding = docVector,
                        ),
                    )
                }
                chunks.forEach { chunk ->
                    add(
                        RagChunkWrite(
                            sourceType = RagSourceType.AUTHOR_POST,
                            sourceId = postId,
                            granularity = RagChunkGranularity.CHUNK,
                            chunkIndex = chunk.chunkIndex,
                            title = title,
                            url = url,
                            company = null,
                            content = chunk.content,
                            embedding = chunk.embedding,
                        ),
                    )
                }
            }
        ragChunkRepository.replaceSource(RagSourceType.AUTHOR_POST, postId, commands)
    }

    @Transactional
    fun replaceExternalArticle(
        articleId: Long,
        title: String,
        url: String,
        company: String,
        content: String,
        docVector: String,
        chunks: List<ChunkEmbedding>,
    ) {
        val commands =
            buildList {
                if (content.isNotBlank()) {
                    add(
                        RagChunkWrite(
                            sourceType = RagSourceType.EXTERNAL_ARTICLE,
                            sourceId = articleId,
                            granularity = RagChunkGranularity.DOCUMENT,
                            chunkIndex = DOCUMENT_CHUNK_INDEX,
                            title = title,
                            url = url,
                            company = company,
                            content = content,
                            embedding = docVector,
                        ),
                    )
                }
                chunks.forEach { chunk ->
                    add(
                        RagChunkWrite(
                            sourceType = RagSourceType.EXTERNAL_ARTICLE,
                            sourceId = articleId,
                            granularity = RagChunkGranularity.CHUNK,
                            chunkIndex = chunk.chunkIndex,
                            title = title,
                            url = url,
                            company = company,
                            content = chunk.content,
                            embedding = chunk.embedding,
                        ),
                    )
                }
            }
        ragChunkRepository.replaceSource(RagSourceType.EXTERNAL_ARTICLE, articleId, commands)
    }

    @Transactional
    fun deleteAuthorPost(postId: Long) {
        ragChunkRepository.deleteSource(RagSourceType.AUTHOR_POST, postId)
    }

    @Transactional
    fun deleteExternalArticle(articleId: Long) {
        ragChunkRepository.deleteSource(RagSourceType.EXTERNAL_ARTICLE, articleId)
    }

    @Transactional
    fun deleteAllExternalArticles() {
        ragChunkRepository.deleteAllBySourceType(RagSourceType.EXTERNAL_ARTICLE)
    }

    companion object {
        private const val DOCUMENT_CHUNK_INDEX = -1
    }
}
