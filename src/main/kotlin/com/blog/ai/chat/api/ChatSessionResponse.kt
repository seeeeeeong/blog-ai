package com.blog.ai.chat.api

import com.blog.ai.chat.domain.ChatMode
import com.blog.ai.chat.infrastructure.session.ChatSessionEntity
import java.util.UUID

data class ChatSessionResponse(
    val sessionId: UUID,
    val mode: ChatMode,
) {
    companion object {
        fun of(session: ChatSessionEntity) =
            ChatSessionResponse(
                sessionId = session.id,
                mode = session.mode,
            )
    }
}
