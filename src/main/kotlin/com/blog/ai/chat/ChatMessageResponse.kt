package com.blog.ai.chat

import com.blog.ai.chat.memory.ChatMessage

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
