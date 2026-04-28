package com.blog.ai.post.similar

import com.blog.ai.post.similar.SimilarArticle

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
