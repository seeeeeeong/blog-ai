package com.blog.ai.chat

import com.blog.ai.global.error.AppException
import com.blog.ai.global.error.ErrorCode
import com.blog.ai.chat.ChatMessageRepository
import com.blog.ai.chat.ChatSessionEntity
import com.blog.ai.chat.ChatSessionRepository
import com.blog.ai.chat.toMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.MessageType
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import java.util.UUID

@Service
class ChatService(
    private val chatClient: ChatClient,
    private val chatClientBuilder: ChatClient.Builder,
    private val chatSessionRepository: ChatSessionRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatRateLimiter: RateLimiter,
    private val chatPreflight: ChatPreflight,
    private val chatQueryPlanner: QueryPlanner,
    private val chatMemory: ChatMemory,
) {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val MESSAGE_HISTORY_LIMIT = 50
        private const val CLARIFY_HISTORY_LIMIT = 6
        private const val CLARIFY_HISTORY_SNIPPET = 200
        private const val MAX_CLARIFY_LENGTH = 200
        const val REWRITTEN_QUERY_PARAM = "rewritten_query"
        private const val CLARIFY_EMERGENCY_FALLBACK =
            "어떤 부분을 더 자세히 알고 싶으신가요?"

        private val CLARIFY_SYSTEM_PROMPT =
            """
            You are a Korean tech-blog chatbot. The user's latest message is
            ambiguous between two intents and the system needs to disambiguate
            before searching:

              (a) designing a "related post recommendation / 관련 게시글 추천"
                  feature.
              (b) asking the chatbot to surface posts similar to a specific
                  article.

            Write ONE short Korean clarifying question (single sentence, under
            ~120 chars). Reference the user's actual phrasing so it feels
            natural — do NOT use a generic template. Do NOT answer the question
            itself, do NOT search anything, do NOT add commentary. Output the
            question only.
            """.trimIndent()
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
        if (plan.intent == QueryPlanner.Intent.CLARIFY) {
            return clarifyResponse(sessionId, question, plan.clarificationQuestion)
        }
        return streamChat(sessionId, question, plan.rewrittenQuery, plan.intent)
    }

    fun remainingMessages(sessionId: UUID): Int = chatRateLimiter.remainingMessages(sessionId)

    private fun clarifyResponse(
        sessionId: UUID,
        question: String,
        plannerHint: String?,
    ): Flux<ServerSentEvent<String>> {
        val sessionKey = sessionId.toString()
        val response = generateClarification(sessionKey, question, plannerHint)
        chatMemory.add(
            sessionKey,
            listOf(UserMessage(question), AssistantMessage(response)),
        )
        return Flux.just(
            ServerSentEvent.builder(response).build(),
            ServerSentEvent.builder<String>("[DONE]").build(),
        )
    }

    private fun generateClarification(
        sessionKey: String,
        question: String,
        plannerHint: String?,
    ): String {
        val historyText = formatClarifyHistory(chatMemory.get(sessionKey))
        return try {
            val raw =
                chatClientBuilder
                    .build()
                    .prompt()
                    .system(CLARIFY_SYSTEM_PROMPT)
                    .user(buildClarifyUserPrompt(historyText, question, plannerHint))
                    .call()
                    .content()
            sanitizeClarification(raw) ?: CLARIFY_EMERGENCY_FALLBACK
        } catch (e: Exception) {
            log.warn(e) { "Clarify generation failed, using emergency fallback" }
            CLARIFY_EMERGENCY_FALLBACK
        }
    }

    private fun sanitizeClarification(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val firstLine =
            trimmed
                .lineSequence()
                .firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return null
        return firstLine.take(MAX_CLARIFY_LENGTH)
    }

    private fun formatClarifyHistory(history: List<Message>): String =
        if (history.isEmpty()) {
            ""
        } else {
            history
                .takeLast(CLARIFY_HISTORY_LIMIT)
                .filter { it.messageType == MessageType.USER || it.messageType == MessageType.ASSISTANT }
                .joinToString("\n") { "${it.messageType.value}: ${it.text?.take(CLARIFY_HISTORY_SNIPPET)}" }
        }

    private fun buildClarifyUserPrompt(
        historyText: String,
        question: String,
        plannerHint: String?,
    ): String {
        val hint = plannerHint?.takeIf { it.isNotBlank() }
        val hintLine = if (hint == null) "" else "\nHint (do not echo verbatim): $hint"
        val historyBlock = if (historyText.isBlank()) "" else "Conversation history:\n$historyText\n\n"
        return "${historyBlock}Latest user message: $question$hintLine"
    }

    private fun streamChat(
        sessionId: UUID,
        question: String,
        rewrittenQuery: String,
        intent: QueryPlanner.Intent,
    ): Flux<ServerSentEvent<String>> =
        chatClient
            .prompt()
            .user(question)
            .advisors { advisor ->
                advisor.param("chat_memory_conversation_id", sessionId.toString())
                advisor.param(REWRITTEN_QUERY_PARAM, rewrittenQuery)
                advisor.param(ArticleRetriever.INTENT_PARAM, intent.name)
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
        throw AppException(ErrorCode.SESSION_NOT_FOUND)
    }
}
