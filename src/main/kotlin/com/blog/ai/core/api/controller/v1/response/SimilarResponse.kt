package com.blog.ai.core.api.controller.v1.response

import com.blog.ai.core.domain.post.SimilarArticle
import com.blog.ai.core.domain.post.SimilarResult
import com.blog.ai.core.domain.post.SimilarStatus

data class SimilarResponse(
    val status: SimilarStatus,
    val items: List<SimilarItem>,
) {
    companion object {
        fun of(result: SimilarResult) =
            SimilarResponse(
                status = result.status,
                items = result.items.map(SimilarItem.Companion::of),
            )
    }
}

data class SimilarItem(
    val id: Long,
    val title: String,
    val url: String,
    val company: String,
    val score: Double,
) {
    companion object {
        fun of(article: SimilarArticle) =
            SimilarItem(
                id = article.id,
                title = article.title,
                url = article.url,
                company = article.company,
                score = article.score,
            )
    }
}
