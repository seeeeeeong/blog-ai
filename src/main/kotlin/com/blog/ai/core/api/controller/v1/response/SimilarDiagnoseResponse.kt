package com.blog.ai.core.api.controller.v1.response

import com.blog.ai.core.domain.post.SimilarDiagnoseResult
import com.blog.ai.core.domain.post.SimilarStatus

data class SimilarDiagnoseResponse(
    val status: SimilarStatus,
    val vectorOnly: List<SimilarItem>,
    val bm25Only: List<SimilarItem>,
    val hybrid: List<SimilarItem>,
) {
    companion object {
        fun of(result: SimilarDiagnoseResult) =
            SimilarDiagnoseResponse(
                status = result.status,
                vectorOnly = result.vectorOnly.map(SimilarItem.Companion::of),
                bm25Only = result.bm25Only.map(SimilarItem.Companion::of),
                hybrid = result.hybrid.map(SimilarItem.Companion::of),
            )
    }
}
