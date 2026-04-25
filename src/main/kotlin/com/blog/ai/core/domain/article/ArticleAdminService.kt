package com.blog.ai.core.domain.article

import com.blog.ai.storage.article.ArticleChunkRepository
import com.blog.ai.storage.article.ArticleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ArticleAdminService(
    private val articleRepository: ArticleRepository,
    private val articleChunkRepository: ArticleChunkRepository,
) {
    @Transactional(propagation = Propagation.REQUIRED)
    fun markAllForReembed(): Int {
        articleChunkRepository.truncateAll()
        return articleRepository.resetAllEmbeddingsForReprocess()
    }
}
