package com.blog.ai.chat.service

import com.blog.ai.chat.entity.ChatSessionEntity
import com.blog.ai.chat.mapper.toMessage
import com.blog.ai.chat.model.ChatMessage
import com.blog.ai.chat.repository.ChatMessageRepository
import com.blog.ai.chat.repository.ChatSessionRepository
import com.blog.ai.global.error.AppException
import com.blog.ai.global.error.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ChatSessionService(
    private val chatSessionRepository: ChatSessionRepository,
    private val chatMessageRepository: ChatMessageRepository,
) {
    companion object {
        private const val MESSAGE_HISTORY_LIMIT = 50
    }

    @Transactional
    fun createSession(): UUID {
        val session = chatSessionRepository.save(ChatSessionEntity.create())
        return session.id
    }

    @Transactional(readOnly = true)
    fun getMessages(sessionId: UUID): List<ChatMessage> {
        if (!chatSessionRepository.existsById(sessionId)) {
            throw AppException(ErrorCode.SESSION_NOT_FOUND)
        }
        return chatMessageRepository
            .findRecentBySessionId(sessionId, MESSAGE_HISTORY_LIMIT)
            .filter { it.role in listOf("user", "assistant") }
            .map { it.toMessage() }
    }
}
