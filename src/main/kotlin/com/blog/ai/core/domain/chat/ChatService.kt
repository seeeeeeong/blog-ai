package com.blog.ai.core.domain.chat

import com.blog.ai.core.support.error.CoreException
import com.blog.ai.core.support.error.ErrorType
import com.blog.ai.storage.chat.ChatMessageRepository
import com.blog.ai.storage.chat.ChatSessionEntity
import com.blog.ai.storage.chat.ChatSessionRepository
import com.blog.ai.storage.chat.toMessage
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import java.util.UUID

@Service
class ChatService(
    private val chatClient: ChatClient,
    private val chatSessionRepository: ChatSessionRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatRateLimiter: ChatRateLimiter,
    private val chatPreflight: ChatPreflight,
    private val chatQueryPlanner: ChatQueryPlanner,
    private val chatMemory: ChatMemory,
) {
    companion object {
        private const val MESSAGE_HISTORY_LIMIT = 50
        const val REWRITTEN_QUERY_PARAM = "rewritten_query"
        private const val CLARIFY_RESPONSE =
            "관련 게시글 추천 기능을 설계하시려는 건가요, " +
                "아니면 특정 글을 기준으로 비슷한 글을 추천받고 싶으신가요?"
    }

    @Transactional
    fun createSession(): UUID {
        val session = chatSessionRepository.save(ChatSessionEntity.create())
        return session.id
    }

    fun chat(
        sessionId: UUID,
        question: String,
        clientIp: String,
    ): Flux<ServerSentEvent<String>> {
        chatPreflight.consumeOrThrow(sessionId, clientIp)
        val plan = chatQueryPlanner.plan(sessionId.toString(), question)
        if (plan.intent == ChatQueryPlanner.Intent.CLARIFY) {
            return clarifyResponse(sessionId, question)
        }
        return streamChat(sessionId, question, plan.rewrittenQuery)
    }

    fun remainingMessages(sessionId: UUID): Int = chatRateLimiter.remainingMessages(sessionId)

    private fun clarifyResponse(
        sessionId: UUID,
        question: String,
    ): Flux<ServerSentEvent<String>> {
        chatMemory.add(
            sessionId.toString(),
            listOf(UserMessage(question), AssistantMessage(CLARIFY_RESPONSE)),
        )
        return Flux.just(
            ServerSentEvent.builder(CLARIFY_RESPONSE).build(),
            ServerSentEvent.builder<String>("[DONE]").build(),
        )
    }

    private fun streamChat(
        sessionId: UUID,
        question: String,
        rewrittenQuery: String,
    ): Flux<ServerSentEvent<String>> =
        chatClient
            .prompt()
            .user(question)
            .advisors { advisor ->
                advisor.param("chat_memory_conversation_id", sessionId.toString())
                advisor.param(REWRITTEN_QUERY_PARAM, rewrittenQuery)
            }.stream()
            .content()
            .map { content -> ServerSentEvent.builder(content).build() }
            .concatWith(Flux.just(ServerSentEvent.builder<String>("[DONE]").build()))

    @Transactional(readOnly = true)
    fun getMessages(sessionId: UUID): List<ChatMessage> {
        val sessionExists = chatSessionRepository.existsById(sessionId)
        if (sessionExists) {
            return chatMessageRepository
                .findRecentBySessionId(sessionId, MESSAGE_HISTORY_LIMIT)
                .filter { it.role in listOf("user", "assistant") }
                .map { it.toMessage() }
        }
        throw CoreException(ErrorType.SESSION_NOT_FOUND)
    }
}
