package com.blog.ai.core.api.controller.v1.response

import com.blog.ai.chat.ChatMessage

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
