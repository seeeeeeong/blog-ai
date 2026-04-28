package com.blog.ai.post.similar

import com.blog.ai.post.similar.SimilarResult
import com.blog.ai.post.similar.SimilarStatus

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
