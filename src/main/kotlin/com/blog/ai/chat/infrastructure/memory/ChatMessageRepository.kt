package com.blog.ai.chat.infrastructure.memory

import com.blog.ai.chat.infrastructure.memory.ChatMessageEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID

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
