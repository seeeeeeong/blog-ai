package com.blog.ai.core.api.controller.v1.response

import com.blog.ai.storage.chat.ChatMessageEntity

data class ChatMessageResponse(
    val role: String,
    val content: String,
) {
    companion object {
        fun of(entity: ChatMessageEntity) =
            ChatMessageResponse(
                role = entity.role,
                content = entity.content,
            )
    }
}
