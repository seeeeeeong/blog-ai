package com.blog.ai.core.api.controller.v1.response

import com.blog.ai.core.domain.article.ArticleAdmin
import java.time.OffsetDateTime

data class ArticleAdminResponse(
    val id: Long,
    val title: String,
    val url: String,
    val urlHash: String,
    val company: String,
    val embedded: Boolean,
    val embedError: String?,
    val crawledAt: OffsetDateTime,
) {
    companion object {
        fun of(article: ArticleAdmin) = ArticleAdminResponse(
            id = article.id,
            title = article.title,
            url = article.url,
            urlHash = article.urlHash,
            company = article.company,
            embedded = article.embedded,
            embedError = article.embedError,
            crawledAt = article.crawledAt,
        )
    }
}
