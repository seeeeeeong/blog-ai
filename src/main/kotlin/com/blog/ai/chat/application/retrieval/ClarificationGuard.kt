package com.blog.ai.chat.application.retrieval

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

@Component
class ClarificationGuard {
    private val cache =
        Caffeine
            .newBuilder()
            .expireAfterWrite(Duration.ofMinutes(TTL_MINUTES))
            .maximumSize(MAX_ENTRIES)
            .build<UUID, Boolean>()

    fun mark(sessionId: UUID) {
        cache.put(sessionId, true)
    }

    fun consume(sessionId: UUID): Boolean {
        val present = cache.getIfPresent(sessionId) != null
        if (present) cache.invalidate(sessionId)
        return present
    }

    companion object {
        private const val TTL_MINUTES = 10L
        private const val MAX_ENTRIES = 10_000L
    }
}
