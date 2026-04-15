package com.blog.ai.core.domain.article

import com.blog.ai.storage.article.ArticleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ArticleAdminService(
    private val articleRepository: ArticleRepository,
) {

    fun findArticlesForAdmin(limit: Int, offset: Int): List<Array<Any>> {
        return articleRepository.findArticlesForAdmin(limit, offset)
    }

    fun countTotal(): Long {
        return articleRepository.count()
    }

    fun countUnembedded(): Long {
        return articleRepository.countUnembedded()
    }
}
