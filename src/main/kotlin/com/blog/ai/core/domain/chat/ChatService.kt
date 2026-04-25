package com.blog.ai.core.domain.chat

import com.blog.ai.core.support.error.CoreException
import com.blog.ai.core.support.error.ErrorType
import com.blog.ai.storage.chat.ChatMessageRepository
import com.blog.ai.storage.chat.ChatSessionEntity
import com.blog.ai.storage.chat.ChatSessionRepository
import com.blog.ai.storage.chat.toMessage
import org.springframework.ai.chat.client.ChatClient
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ChatService(
    private val chatClient: ChatClient,
    private val chatSessionRepository: ChatSessionRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatRateLimiter: ChatRateLimiter,
) {
    companion object {
        private const val MESSAGE_HISTORY_LIMIT = 50
    }

    @Transactional
    fun createSession(): UUID {
        val session = chatSessionRepository.save(ChatSessionEntity.create())
        return session.id
    }

    @Transactional
    fun chat(
        sessionId: UUID,
        question: String,
        clientIp: String,
    ): Flux<ServerSentEvent<String>> {
        requireSessionExists(sessionId)
        chatRateLimiter.checkAndIncrement(sessionId, clientIp)
        return streamChat(sessionId, question)
    }

    fun remainingMessages(sessionId: UUID): Int = chatRateLimiter.remainingMessages(sessionId)

    private fun streamChat(
        sessionId: UUID,
        question: String,
    ): Flux<ServerSentEvent<String>> =
        chatClient
            .prompt()
            .user(question)
            .advisors { advisor ->
                advisor.param("chat_memory_conversation_id", sessionId.toString())
            }.stream()
            .content()
            .map { content -> ServerSentEvent.builder(content).build() }
            .concatWith(Flux.just(ServerSentEvent.builder<String>("[DONE]").build()))

    fun getMessages(sessionId: UUID): List<ChatMessage> {
        requireSessionExists(sessionId)
        return chatMessageRepository
            .findRecentBySessionId(sessionId, MESSAGE_HISTORY_LIMIT)
            .filter { it.role in listOf("user", "assistant") }
            .map { it.toMessage() }
    }

    private fun requireSessionExists(sessionId: UUID) {
        val sessionExists = chatSessionRepository.existsById(sessionId)
        if (sessionExists) return
        throw CoreException(ErrorType.SESSION_NOT_FOUND)
    }
}
