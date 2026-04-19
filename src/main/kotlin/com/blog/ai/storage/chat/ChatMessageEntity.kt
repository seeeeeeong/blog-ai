package com.blog.ai.storage.chat

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

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
