package com.blog.ai.chat.infrastructure.session

import com.blog.ai.chat.domain.ChatMode
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "chat_sessions")
class ChatSessionEntity(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false)
    val mode: ChatMode = ChatMode.DEFAULT,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
) {
    companion object {
        fun create(mode: ChatMode = ChatMode.DEFAULT): ChatSessionEntity = ChatSessionEntity(mode = mode)
    }
}
