package com.blog.ai.post.similar

data class SimilarResult(
    val status: SimilarStatus,
    val items: List<SimilarArticle>,
) {
    companion object {
        fun ready(items: List<SimilarArticle>) = SimilarResult(SimilarStatus.READY, items)

        fun pending() = SimilarResult(SimilarStatus.PENDING, emptyList())

        fun notFound() = SimilarResult(SimilarStatus.NOT_FOUND, emptyList())

        fun deleted() = SimilarResult(SimilarStatus.DELETED, emptyList())
    }
}
