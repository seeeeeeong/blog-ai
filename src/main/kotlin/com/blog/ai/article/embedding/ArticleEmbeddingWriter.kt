package com.blog.ai.article.embedding

import com.blog.ai.article.ArticleRepository
import com.blog.ai.article.embedding.ArticleEmbeddingResult
import com.blog.ai.rag.RagWriteService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ArticleEmbeddingWriter(
    private val articleRepository: ArticleRepository,
    private val ragWriteService: RagWriteService,
) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Transactional
    fun commit(command: ArticleEmbeddingResult) {
        articleRepository.markEmbedded(command.articleId)
        ragWriteService.replaceExternalArticle(
            articleId = command.articleId,
            title = command.title,
            url = command.url,
            company = command.company,
            content = command.content,
            docVector = command.docVector,
            chunks = command.chunks,
        )
        log.debug { "Article embedding committed: id=${command.articleId}" }
    }

    @Transactional
    fun recordError(
        articleId: Long,
        message: String,
    ) {
        articleRepository.updateEmbedError(articleId, message)
    }
}
