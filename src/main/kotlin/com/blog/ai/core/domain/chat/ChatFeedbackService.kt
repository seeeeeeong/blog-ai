package com.blog.ai.core.domain.chat

import com.blog.ai.core.support.error.CoreException
import com.blog.ai.core.support.error.ErrorType
import com.blog.ai.storage.chat.ChatFeedbackEntity
import com.blog.ai.storage.chat.ChatFeedbackRepository
import com.blog.ai.storage.chat.ChatSessionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ChatFeedbackService(
    private val chatFeedbackRepository: ChatFeedbackRepository,
    private val chatSessionRepository: ChatSessionRepository,
) {
    @Transactional
    fun save(
        sessionId: UUID,
        messageId: String,
        rating: String,
    ) {
        requireValidRating(rating)
        requireSessionExists(sessionId)
        chatFeedbackRepository.save(
            ChatFeedbackEntity.create(sessionId, messageId, rating),
        )
    }

    private fun requireValidRating(rating: String) {
        if (rating !in VALID_RATINGS) {
            throw CoreException(ErrorType.INVALID_FEEDBACK)
        }
    }

    private fun requireSessionExists(sessionId: UUID) {
        val sessionExists = chatSessionRepository.existsById(sessionId)
        if (sessionExists) return
        throw CoreException(ErrorType.SESSION_NOT_FOUND)
    }

    companion object {
        private val VALID_RATINGS = setOf("up", "down")
    }
}
