package com.blog.ai.core.domain.chat

import com.blog.ai.core.support.error.CoreException
import com.blog.ai.core.support.error.ErrorType
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@Component
class ChatRateLimiter {
    companion object {
        private const val MAX_MESSAGES_PER_SESSION = 30
        private const val MAX_MESSAGES_PER_IP_PER_HOUR = 60
    }

    private val sessionCounters =
        Caffeine
            .newBuilder()
            .expireAfterWrite(Duration.ofHours(24))
            .maximumSize(10_000)
            .build<UUID, AtomicInteger>()

    private val ipCounters =
        Caffeine
            .newBuilder()
            .expireAfterWrite(Duration.ofHours(1))
            .maximumSize(50_000)
            .build<String, AtomicInteger>()

    fun checkAndIncrement(
        sessionId: UUID,
        clientIp: String,
    ) {
        val sessionCount = sessionCounters.get(sessionId) { AtomicInteger(0) }
        if (sessionCount.get() >= MAX_MESSAGES_PER_SESSION) {
            throw CoreException(ErrorType.CHAT_RATE_LIMITED)
        }

        val ipCount = ipCounters.get(clientIp) { AtomicInteger(0) }
        if (ipCount.get() >= MAX_MESSAGES_PER_IP_PER_HOUR) {
            throw CoreException(ErrorType.CHAT_RATE_LIMITED)
        }

        sessionCount.incrementAndGet()
        ipCount.incrementAndGet()
    }

    fun remainingMessages(sessionId: UUID): Int {
        val count = sessionCounters.getIfPresent(sessionId)?.get() ?: 0
        return (MAX_MESSAGES_PER_SESSION - count).coerceAtLeast(0)
    }
}
