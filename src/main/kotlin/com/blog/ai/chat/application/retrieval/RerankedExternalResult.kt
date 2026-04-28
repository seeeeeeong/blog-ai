package com.blog.ai.chat.application.retrieval

import org.springframework.ai.document.Document

data class RerankedExternalResult(
    val docs: List<Document>,
    val topScore: Double?,
    val abstained: Boolean,
    val rerankUnavailable: Boolean = false,
) {
    companion object {
        fun empty(): RerankedExternalResult = RerankedExternalResult(emptyList(), null, false)
    }
}
