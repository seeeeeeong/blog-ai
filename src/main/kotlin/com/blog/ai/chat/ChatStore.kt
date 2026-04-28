package com.blog.ai.chat

import com.blog.ai.chat.ChatMessage
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "chat_sessions")
class ChatSessionEntity(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
) {
    companion object {
        fun create(): ChatSessionEntity = ChatSessionEntity()
    }
}

interface ChatSessionRepository : JpaRepository<ChatSessionEntity, UUID>

@Entity
@Table(name = "chat_messages")
class ChatMessageEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "session_id", nullable = false)
    val sessionId: UUID,
    @Column(nullable = false, length = 16)
    val role: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
) {
    companion object {
        fun create(
            sessionId: UUID,
            role: String,
            content: String,
        ): ChatMessageEntity {
            require(role.isNotBlank()) { "role must not be blank" }
            require(content.isNotBlank()) { "content must not be blank" }
            return ChatMessageEntity(
                sessionId = sessionId,
                role = role,
                content = content,
            )
        }
    }
}

fun ChatMessageEntity.toMessage() = ChatMessage(role = role, content = content)

interface ChatMessageRepository : JpaRepository<ChatMessageEntity, Long> {
    @Query(
        value = """
            SELECT * FROM (
                SELECT * FROM chat_messages
                WHERE session_id = :sessionId
                ORDER BY id DESC
                LIMIT :limit
            ) recent
            ORDER BY id ASC
        """,
        nativeQuery = true,
    )
    fun findRecentBySessionId(
        sessionId: UUID,
        limit: Int,
    ): List<ChatMessageEntity>

    @Modifying
    @Query("DELETE FROM ChatMessageEntity m WHERE m.sessionId = :sessionId")
    fun deleteBySessionId(sessionId: UUID)
}

@Repository
class ChatRateLimitRepository(
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

data class RateLimitRequest(
    val sessionKey: String,
    val ipKey: String,
    val sessionMax: Int,
    val ipMax: Int,
    val sessionTtl: Duration,
    val ipTtl: Duration,
)

enum class RateLimitOutcome {
    OK,
    SESSION_LIMITED,
    IP_LIMITED,
}
