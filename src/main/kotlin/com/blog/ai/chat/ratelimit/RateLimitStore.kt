package com.blog.ai.chat.ratelimit

import com.blog.ai.chat.ratelimit.RateLimitOutcome
import com.blog.ai.chat.ratelimit.RateLimitRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

@Repository
class RateLimitStore(
    private val jdbcTemplate: JdbcTemplate,
) {
    companion object {
        const val SCOPE_SESSION = "session"
        const val SCOPE_IP_HOUR = "ip_hour"
    }

    @Transactional
    fun tryConsume(request: RateLimitRequest): RateLimitOutcome {
        ensureRow(request.sessionKey, SCOPE_SESSION)
        ensureRow(request.ipKey, SCOPE_IP_HOUR)

        val sessionCount = lockedActiveCount(SCOPE_SESSION, request.sessionKey)
        if (sessionCount >= request.sessionMax) return RateLimitOutcome.SESSION_LIMITED

        val ipCount = lockedActiveCount(SCOPE_IP_HOUR, request.ipKey)
        if (ipCount >= request.ipMax) return RateLimitOutcome.IP_LIMITED

        bump(SCOPE_SESSION, request.sessionKey, request.sessionTtl)
        bump(SCOPE_IP_HOUR, request.ipKey, request.ipTtl)
        return RateLimitOutcome.OK
    }

    fun getActiveCount(
        scope: String,
        key: String,
    ): Int =
        jdbcTemplate
            .query(
                """
                SELECT count FROM chat_rate_limit
                WHERE scope = ? AND key = ? AND reset_at > NOW()
                """.trimIndent(),
                { rs, _ -> rs.getInt("count") },
                scope,
                key,
            ).firstOrNull() ?: 0

    fun deleteExpired(): Int = jdbcTemplate.update("DELETE FROM chat_rate_limit WHERE reset_at <= NOW()")

    private fun ensureRow(
        key: String,
        scope: String,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO chat_rate_limit (scope, key, count, reset_at)
            VALUES (?, ?, 0, 'epoch'::timestamp)
            ON CONFLICT (scope, key) DO NOTHING
            """.trimIndent(),
            scope,
            key,
        )
    }

    private fun lockedActiveCount(
        scope: String,
        key: String,
    ): Int =
        jdbcTemplate
            .query(
                """
                SELECT CASE WHEN reset_at > NOW() THEN count ELSE 0 END AS effective_count
                FROM chat_rate_limit
                WHERE scope = ? AND key = ?
                FOR UPDATE
                """.trimIndent(),
                { rs, _ -> rs.getInt("effective_count") },
                scope,
                key,
            ).firstOrNull() ?: 0

    private fun bump(
        scope: String,
        key: String,
        ttl: Duration,
    ) {
        jdbcTemplate.update(
            """
            UPDATE chat_rate_limit
               SET count = CASE WHEN reset_at <= NOW() THEN 1 ELSE count + 1 END,
                   reset_at = CASE WHEN reset_at <= NOW()
                                   THEN NOW() + make_interval(secs => ?)
                                   ELSE reset_at END
             WHERE scope = ? AND key = ?
            """.trimIndent(),
            ttl.toSeconds().toDouble(),
            scope,
            key,
        )
    }
}
