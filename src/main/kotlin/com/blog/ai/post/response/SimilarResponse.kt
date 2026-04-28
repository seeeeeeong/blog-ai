package com.blog.ai.post.response

import com.blog.ai.post.model.SimilarResult
import com.blog.ai.post.model.SimilarStatus

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
