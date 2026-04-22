package com.blog.ai.core.domain.article

import com.blog.ai.storage.article.ArticleChunkRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ArticleChunkService(
    private val articleChunkRepository: ArticleChunkRepository,
) {
    @Transactional
    fun replaceChunks(
        articleId: Long,
        chunks: List<SaveChunkCommand>,
    ) {
        articleChunkRepository.deleteByArticleId(articleId)
        chunks.forEach(articleChunkRepository::saveChunk)
    }
}
