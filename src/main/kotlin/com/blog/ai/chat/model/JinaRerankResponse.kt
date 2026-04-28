package com.blog.ai.chat.model

import com.fasterxml.jackson.annotation.JsonProperty

data class JinaRerankResponse(
    val results: List<JinaRerankResult> = emptyList(),
)

data class JinaRerankResult(
    val index: Int,
    @JsonProperty("relevance_score") val relevanceScore: Double,
)
