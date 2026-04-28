package com.blog.ai.post.domain

import com.blog.ai.rag.domain.RagChunkHit

data class SimilarArticle(
    val id: Long,
    val title: String,
    val url: String,
    val company: String,
    val score: Double,
) {
    companion object {
        fun of(hit: RagChunkHit): SimilarArticle =
            SimilarArticle(
                id = hit.sourceId,
                title = hit.title,
                url = hit.url.orEmpty(),
                company = hit.company.orEmpty(),
                score = hit.score,
            )
    }
}
