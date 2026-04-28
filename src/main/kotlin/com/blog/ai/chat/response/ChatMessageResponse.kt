package com.blog.ai.chat.response

import com.blog.ai.chat.model.ChatMessage

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
