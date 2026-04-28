package com.blog.ai.chat.memory

import com.blog.ai.chat.entity.ChatMessageEntity
import com.blog.ai.chat.repository.ChatMessageRepository
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.MessageType
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class ChatMemoryStore(
    private val chatMessageRepository: ChatMessageRepository,
) : ChatMemory {
    companion object {
        private const val WINDOW_SIZE = 20
    }

    @Transactional
    override fun add(
        conversationId: String,
        messages: List<Message>,
    ) {
        val sessionId = UUID.fromString(conversationId)
        val entities =
            messages
                .filter { it.text?.isNotBlank() == true }
                .map { ChatMessageEntity.create(sessionId, it.messageType.value, it.text) }
        if (entities.isNotEmpty()) chatMessageRepository.saveAll(entities)
    }

    @Transactional(readOnly = true)
    override fun get(conversationId: String): List<Message> {
        val sessionId = UUID.fromString(conversationId)
        return chatMessageRepository
            .findRecentBySessionId(sessionId, WINDOW_SIZE)
            .mapNotNull(::toMessage)
    }

    @Transactional
    override fun clear(conversationId: String) {
        chatMessageRepository.deleteBySessionId(UUID.fromString(conversationId))
    }

    private fun toMessage(entity: ChatMessageEntity): Message? =
        when (MessageType.fromValue(entity.role)) {
            MessageType.USER -> UserMessage(entity.content)
            MessageType.ASSISTANT -> AssistantMessage(entity.content)
            MessageType.SYSTEM -> SystemMessage(entity.content)
            MessageType.TOOL -> null
        }
}
