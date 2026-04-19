package com.blog.ai.core.domain.post

data class SimilarDiagnoseResult(
    val status: SimilarStatus,
    val vectorOnly: List<SimilarArticle>,
    val bm25Only: List<SimilarArticle>,
    val hybrid: List<SimilarArticle>,
) {
    companion object {
        fun ready(
            vectorOnly: List<SimilarArticle>,
            bm25Only: List<SimilarArticle>,
            hybrid: List<SimilarArticle>,
        ) = SimilarDiagnoseResult(SimilarStatus.READY, vectorOnly, bm25Only, hybrid)

        fun pending() = SimilarDiagnoseResult(SimilarStatus.PENDING, emptyList(), emptyList(), emptyList())

        fun notFound() = SimilarDiagnoseResult(SimilarStatus.NOT_FOUND, emptyList(), emptyList(), emptyList())

        fun deleted() = SimilarDiagnoseResult(SimilarStatus.DELETED, emptyList(), emptyList(), emptyList())
    }
}
