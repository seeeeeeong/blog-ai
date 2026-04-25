package com.blog.ai.core.domain.article

import com.blog.ai.storage.article.ArticleRepository
import com.blog.ai.storage.rag.RagChunkRepository
import com.blog.ai.storage.rag.RagSourceType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ArticleAdminService(
    private val articleRepository: ArticleRepository,
    private val ragChunkRepository: RagChunkRepository,
) {
    @Transactional(propagation = Propagation.REQUIRED)
    fun markAllForReembed(): Int {
        ragChunkRepository.deleteAllBySourceType(RagSourceType.EXTERNAL_ARTICLE)
        return articleRepository.resetAllEmbeddingsForReprocess()
    }
}
