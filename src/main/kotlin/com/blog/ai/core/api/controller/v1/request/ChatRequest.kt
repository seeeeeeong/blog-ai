package com.blog.ai.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class ChatRequest(
    val sessionId: UUID,
    @field:NotBlank
    @field:Size(max = 500)
    val question: String,
)
