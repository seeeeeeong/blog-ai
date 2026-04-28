package com.blog.ai.chat.memory

import com.blog.ai.chat.memory.ChatMessage
import com.blog.ai.chat.memory.ChatMessageEntity

fun ChatMessageEntity.toMessage() = ChatMessage(role = role, content = content)
