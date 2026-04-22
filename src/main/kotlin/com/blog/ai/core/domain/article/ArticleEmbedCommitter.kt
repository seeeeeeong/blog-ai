package com.blog.ai.core.domain.article

import com.blog.ai.storage.article.ArticleRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ArticleEmbedCommitter(
    private val articleRepository: ArticleRepository,
    private val articleChunkService: ArticleChunkService,
) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Transactional
    fun commit(command: ArticleEmbedCommitCommand) {
        articleRepository.updateEmbedding(command.articleId, command.docVector, command.title, command.content)
        articleChunkService.replaceChunks(command.articleId, command.chunks)
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

data class ArticleEmbedCommitCommand(
    val articleId: Long,
    val title: String,
    val content: String,
    val docVector: String,
    val chunks: List<SaveChunkCommand>,
)
