package com.blog.ai.storage.chat

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.Hibernate
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as ChatSessionEntity
        return id == other.id
    }

    override fun hashCode(): Int = Hibernate.getClass(this).hashCode()
}
