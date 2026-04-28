package com.blog.ai.core.domain.chat

import com.blog.ai.global.error.AppException
import com.blog.ai.global.error.ErrorCode
import com.blog.ai.chat.ChatSessionRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class ChatPreflight(
    private val chatSessionRepository: ChatSessionRepository,
    private val chatRateLimiter: ChatRateLimiter,
) {
    @Transactional
    fun consumeOrThrow(
        sessionId: UUID,
        clientIp: String,
    ) {
        val sessionExists = chatSessionRepository.existsById(sessionId)
        if (sessionExists) {
            chatRateLimiter.checkAndIncrement(sessionId, clientIp)
            return
        }
        throw AppException(ErrorCode.SESSION_NOT_FOUND)
    }
}
