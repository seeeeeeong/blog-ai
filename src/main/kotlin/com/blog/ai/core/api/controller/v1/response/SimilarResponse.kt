package com.blog.ai.core.api.controller.v1.response

import com.blog.ai.core.domain.similar.SimilarArticle

data class SimilarResponse(
    val id: Long,
    val title: String,
    val url: String,
    val company: String,
    val score: Double,
) {
    companion object {
        fun of(article: SimilarArticle) = SimilarResponse(
            id = article.id,
            title = article.title,
            url = article.url,
            company = article.company,
            score = article.score,
        )
    }
}
