package com.blog.ai.chat.session

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
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
