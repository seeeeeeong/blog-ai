package com.blog.ai.chat.api

import com.blog.ai.chat.domain.ChatMessage

data class ChatMessageResponse(
    val role: String,
    val content: String,
) {
    companion object {
        fun of(message: ChatMessage) =
            ChatMessageResponse(
                role = message.role,
                content = message.content,
            )
    }
}
