package com.blog.ai.article.service

import com.blog.ai.article.repository.ArticleRepository
import com.blog.ai.rag.model.RagSourceType
import com.blog.ai.rag.repository.RagChunkRepository
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
