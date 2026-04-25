package com.blog.ai.core.domain.rag

import com.blog.ai.core.domain.article.SaveChunkCommand
import com.blog.ai.core.domain.post.SaveBlogPostChunkCommand
import com.blog.ai.storage.rag.RagChunkCommand
import com.blog.ai.storage.rag.RagChunkGranularity
import com.blog.ai.storage.rag.RagChunkRepository
import com.blog.ai.storage.rag.RagSourceType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RagChunkService(
    private val ragChunkRepository: RagChunkRepository,
) {
    @Transactional
    fun replaceAuthorPost(
        postId: Long,
        title: String,
        url: String?,
        content: String,
        docVector: String,
        chunks: List<SaveBlogPostChunkCommand>,
    ) {
        val commands =
            buildList {
                if (content.isNotBlank()) {
                    add(
                        RagChunkCommand(
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
                        RagChunkCommand(
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
        chunks: List<SaveChunkCommand>,
    ) {
        val commands =
            buildList {
                if (content.isNotBlank()) {
                    add(
                        RagChunkCommand(
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
                        RagChunkCommand(
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

    companion object {
        private const val DOCUMENT_CHUNK_INDEX = -1
    }
}
