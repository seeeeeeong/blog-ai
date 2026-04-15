package com.blog.ai.core.api.controller.v1.response

data class TrendingResponse(
    val title: String,
    val url: String?,
    val score: Int,
)
