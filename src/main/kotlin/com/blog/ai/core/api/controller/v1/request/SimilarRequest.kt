package com.blog.ai.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank

data class SimilarRequest(
    @field:NotBlank
    val vector: String,
)
