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
@Table(name = "chat_feedback")
class ChatFeedbackEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "session_id", nullable = false)
    val sessionId: UUID,
    @Column(name = "message_id", nullable = false, length = 64)
    val messageId: String,
    @Column(nullable = false, length = 8)
    val rating: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
) {
    companion object {
        private val VALID_RATINGS = setOf("up", "down")

        fun create(
            sessionId: UUID,
            messageId: String,
            rating: String,
        ): ChatFeedbackEntity {
            require(rating in VALID_RATINGS) { "rating must be 'up' or 'down'" }
            return ChatFeedbackEntity(
                sessionId = sessionId,
                messageId = messageId,
                rating = rating,
            )
        }
    }
}
