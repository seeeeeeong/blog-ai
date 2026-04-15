package com.blog.ai.core.api.controller.v1.request

import java.util.UUID

data class ChatRequest(
    val sessionId: UUID,
    val question: String,
)
