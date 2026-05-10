package com.blog.ai.chat.application.ratelimit

import com.blog.ai.chat.infrastructure.session.ChatSessionRepository
import com.blog.ai.global.error.AppException
import com.blog.ai.global.error.ErrorCode
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class ChatPreflight(
    private val chatSessionRepository: ChatSessionRepository,
    private val chatRateLimiter: RateLimiter,
) {
    @Transactional
    fun consumeOrThrow(
        sessionId: UUID,
        clientIp: String,
    ) {
        requireSession(sessionId)
        chatRateLimiter.checkAndIncrement(sessionId, clientIp)
    }

    fun requireSession(sessionId: UUID) {
        if (chatSessionRepository.existsById(sessionId)) return
        throw AppException(ErrorCode.SESSION_NOT_FOUND)
    }
}
