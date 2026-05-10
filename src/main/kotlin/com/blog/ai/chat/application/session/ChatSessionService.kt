package com.blog.ai.chat.application.session

import com.blog.ai.chat.domain.ChatMessage
import com.blog.ai.chat.domain.ChatMode
import com.blog.ai.chat.infrastructure.memory.ChatMessageRepository
import com.blog.ai.chat.infrastructure.memory.toMessage
import com.blog.ai.chat.infrastructure.session.ChatSessionEntity
import com.blog.ai.chat.infrastructure.session.ChatSessionRepository
import com.blog.ai.global.error.AppException
import com.blog.ai.global.error.ErrorCode
import org.springframework.data.repository.findByIdOrNull
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
    fun createSession(mode: ChatMode = ChatMode.DEFAULT): ChatSessionEntity =
        chatSessionRepository.save(ChatSessionEntity.create(mode))

    @Transactional(readOnly = true)
    fun getMode(sessionId: UUID): ChatMode {
        val session =
            chatSessionRepository.findByIdOrNull(sessionId)
                ?: throw AppException(ErrorCode.SESSION_NOT_FOUND)
        return session.mode
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
