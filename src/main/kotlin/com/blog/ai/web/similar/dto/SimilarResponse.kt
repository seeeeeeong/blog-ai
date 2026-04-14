package com.blog.ai.web.similar.dto

data class SimilarResponse(
    val id: Long,
    val title: String,
    val url: String,
    val company: String,
    val score: Double,
)
