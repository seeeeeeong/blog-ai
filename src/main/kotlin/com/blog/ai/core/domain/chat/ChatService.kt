package com.blog.ai.core.domain.chat

import com.blog.ai.core.support.error.CoreException
import com.blog.ai.core.support.error.ErrorType
import com.blog.ai.storage.chat.ChatSessionEntity
import com.blog.ai.storage.chat.ChatSessionRepository
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
) {

    @Transactional
    fun createSession(): UUID {
        val session = chatSessionRepository.save(ChatSessionEntity())
        return session.id
    }

    fun chat(sessionId: UUID, question: String): Flux<ServerSentEvent<String>> {
        if (!chatSessionRepository.existsById(sessionId)) {
            throw CoreException(ErrorType.SESSION_NOT_FOUND)
        }

        return chatClient.prompt()
            .user(question)
            .advisors { advisor ->
                advisor.param("chat_memory_conversation_id", sessionId.toString())
            }
            .stream()
            .content()
            .map { content -> ServerSentEvent.builder(content).build() }
            .concatWith(Flux.just(ServerSentEvent.builder<String>("[DONE]").build()))
    }
}
