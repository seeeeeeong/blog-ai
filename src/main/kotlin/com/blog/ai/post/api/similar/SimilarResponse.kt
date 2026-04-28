package com.blog.ai.post.api.similar

import com.blog.ai.post.domain.SimilarResult
import com.blog.ai.post.domain.SimilarStatus

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
