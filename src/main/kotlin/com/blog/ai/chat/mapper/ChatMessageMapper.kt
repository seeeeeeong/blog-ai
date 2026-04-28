package com.blog.ai.chat.mapper

import com.blog.ai.chat.entity.ChatMessageEntity
import com.blog.ai.chat.model.ChatMessage

fun ChatMessageEntity.toMessage() = ChatMessage(role = role, content = content)
