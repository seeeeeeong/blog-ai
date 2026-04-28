package com.blog.ai.article.service

import com.blog.ai.article.repository.ArticleRepository
import com.blog.ai.rag.service.RagWriteService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ArticleAdminService(
    private val articleRepository: ArticleRepository,
    private val ragWriteService: RagWriteService,
) {
    @Transactional(propagation = Propagation.REQUIRED)
    fun markAllForReembed(): Int {
        ragWriteService.deleteAllExternalArticles()
        return articleRepository.resetAllEmbeddingsForReprocess()
    }
}
