package com.blog.ai.chat.api

import java.util.UUID

data class ChatSessionResponse(
    val sessionId: UUID,
) {
    companion object {
        fun of(sessionId: UUID) = ChatSessionResponse(sessionId = sessionId)
    }
}
