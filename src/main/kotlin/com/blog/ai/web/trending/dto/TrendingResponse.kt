package com.blog.ai.web.trending.dto

data class TrendingResponse(
    val title: String,
    val url: String?,
    val score: Int,
)
