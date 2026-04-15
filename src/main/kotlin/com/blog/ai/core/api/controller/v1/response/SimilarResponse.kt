package com.blog.ai.core.api.controller.v1.response

data class SimilarResponse(
    val id: Long,
    val title: String,
    val url: String,
    val company: String,
    val score: Double,
)
