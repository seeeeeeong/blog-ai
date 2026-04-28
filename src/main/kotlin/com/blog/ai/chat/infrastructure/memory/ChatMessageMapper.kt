package com.blog.ai.chat.infrastructure.memory

import com.blog.ai.chat.domain.ChatMessage
import com.blog.ai.chat.infrastructure.memory.ChatMessageEntity

fun ChatMessageEntity.toMessage() = ChatMessage(role = role, content = content)
