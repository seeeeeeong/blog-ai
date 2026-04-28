package com.blog.ai.chat

import com.blog.ai.global.error.AppException
import com.blog.ai.global.error.ErrorCode
import com.blog.ai.chat.ChatRateLimitRepository
import com.blog.ai.chat.RateLimitOutcome
import com.blog.ai.chat.RateLimitRequest
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

@Component
class RateLimiter(
    private val chatRateLimitRepository: ChatRateLimitRepository,
) {
    companion object {
        private const val MAX_MESSAGES_PER_SESSION = 30
        private const val MAX_MESSAGES_PER_IP_PER_HOUR = 60
        private val SESSION_TTL: Duration = Duration.ofHours(24)
        private val IP_TTL: Duration = Duration.ofHours(1)
    }

    fun checkAndIncrement(
        sessionId: UUID,
        clientIp: String,
    ) {
        val outcome =
            chatRateLimitRepository.tryConsume(
                RateLimitRequest(
                    sessionKey = sessionId.toString(),
                    ipKey = clientIp,
                    sessionMax = MAX_MESSAGES_PER_SESSION,
                    ipMax = MAX_MESSAGES_PER_IP_PER_HOUR,
                    sessionTtl = SESSION_TTL,
                    ipTtl = IP_TTL,
                ),
            )
        if (outcome != RateLimitOutcome.OK) throw AppException(ErrorCode.CHAT_RATE_LIMITED)
    }

    fun remainingMessages(sessionId: UUID): Int {
        val used = chatRateLimitRepository.getActiveCount(ChatRateLimitRepository.SCOPE_SESSION, sessionId.toString())
        return (MAX_MESSAGES_PER_SESSION - used).coerceAtLeast(0)
    }

    fun cleanupExpired(): Int = chatRateLimitRepository.deleteExpired()
}
