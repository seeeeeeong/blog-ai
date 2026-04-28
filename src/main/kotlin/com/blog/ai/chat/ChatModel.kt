@file:Suppress("ktlint:standard:filename")

package com.blog.ai.chat

data class ChatMessage(
    val role: String,
    val content: String,
)
