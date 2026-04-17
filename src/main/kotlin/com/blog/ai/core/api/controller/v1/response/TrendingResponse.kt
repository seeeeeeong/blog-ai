package com.blog.ai.core.api.controller.v1.response

import com.blog.ai.core.domain.trending.HnItem

data class TrendingResponse(
    val title: String,
    val url: String,
    val score: Int,
) {
    companion object {
        fun of(item: HnItem) = TrendingResponse(
            title = item.title,
            url = item.url.orEmpty(),
            score = item.score,
        )
    }
}
