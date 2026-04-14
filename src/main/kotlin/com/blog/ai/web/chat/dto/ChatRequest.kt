package com.blog.ai.web.chat.dto

import java.util.UUID

data class ChatRequest(
    val sessionId: UUID,
    val question: String,
)
