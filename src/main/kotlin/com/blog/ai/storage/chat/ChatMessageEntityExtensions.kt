package com.blog.ai.storage.chat

import com.blog.ai.core.domain.chat.ChatMessage

fun ChatMessageEntity.toMessage() = ChatMessage(role = role, content = content)
