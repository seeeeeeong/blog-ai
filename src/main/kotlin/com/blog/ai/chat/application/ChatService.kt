package com.blog.ai.chat.application

import com.blog.ai.chat.application.ratelimit.ChatPreflight
import com.blog.ai.chat.application.ratelimit.RateLimiter
import com.blog.ai.chat.application.retrieval.ArticleRetriever
import com.blog.ai.chat.application.retrieval.ClarificationGuard
import com.blog.ai.chat.application.retrieval.ClarificationService
import com.blog.ai.chat.application.retrieval.QueryPlanner
import com.blog.ai.chat.application.session.ChatSessionService
import com.blog.ai.chat.domain.ChatAdvisorParams
import com.blog.ai.chat.domain.ChatMode
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.messages.MessageType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import java.util.UUID

@Service
class ChatService(
    private val chatClient: ChatClient,
    private val chatRateLimiter: RateLimiter,
    private val chatPreflight: ChatPreflight,
    private val chatSessionService: ChatSessionService,
    private val chatQueryPlanner: QueryPlanner,
    private val clarificationService: ClarificationService,
    private val clarificationGuard: ClarificationGuard,
    private val chatMemory: ChatMemory,
) {
    fun chat(
        sessionId: UUID,
        question: String,
        clientIp: String,
    ): Flux<ServerSentEvent<String>> {
        chatPreflight.consumeOrThrow(sessionId, clientIp)
        val mode = chatSessionService.getMode(sessionId)
        val rawPlan = chatQueryPlanner.plan(sessionId.toString(), question)
        val plan = applyClarificationGuard(sessionId, question, rawPlan)
        if (plan.intent == QueryPlanner.Intent.CLARIFY) {
            clarificationGuard.mark(sessionId)
            return clarifyResponse(sessionId, question, plan.clarificationQuestion)
        }
        return streamChat(sessionId, question, plan.rewrittenQuery, plan.intent, mode)
    }

    fun remainingMessages(sessionId: UUID): Int = chatRateLimiter.remainingMessages(sessionId)

    private fun applyClarificationGuard(
        sessionId: UUID,
        latestQuestion: String,
        plan: QueryPlanner.Plan,
    ): QueryPlanner.Plan {
        val followsClarification = clarificationGuard.consume(sessionId)
        if (!followsClarification || plan.intent != QueryPlanner.Intent.CLARIFY) return plan
        val merged = mergeWithPriorUserTurn(sessionId, latestQuestion)
        log.info {
            "Clarification guard fired: forcing GENERAL after prior CLARIFY " +
                "(session=$sessionId, merged='${merged.take(80)}')"
        }
        return plan.copy(intent = QueryPlanner.Intent.GENERAL, rewrittenQuery = merged)
    }

    private fun mergeWithPriorUserTurn(
        sessionId: UUID,
        latestQuestion: String,
    ): String {
        val priorUser =
            chatMemory
                .get(sessionId.toString())
                .lastOrNull { it.messageType == MessageType.USER }
                ?.text
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return latestQuestion
        return "$priorUser $latestQuestion"
    }

    private fun clarifyResponse(
        sessionId: UUID,
        question: String,
        plannerHint: String?,
    ): Flux<ServerSentEvent<String>> {
        val response = clarificationService.clarify(sessionId.toString(), question, plannerHint)
        return Flux.just(
            ServerSentEvent.builder(response).build(),
            ServerSentEvent.builder<String>("[DONE]").build(),
        )
    }

    private fun streamChat(
        sessionId: UUID,
        question: String,
        rewrittenQuery: String,
        intent: QueryPlanner.Intent,
        mode: ChatMode,
    ): Flux<ServerSentEvent<String>> =
        chatClient
            .prompt()
            .user(question)
            .advisors { advisor ->
                advisor.param("chat_memory_conversation_id", sessionId.toString())
                advisor.param(ChatAdvisorParams.REWRITTEN_QUERY, rewrittenQuery)
                advisor.param(ArticleRetriever.INTENT_PARAM, intent.name)
                advisor.param(ChatAdvisorParams.MODE, mode.name)
            }.stream()
            .content()
            .map { content -> ServerSentEvent.builder(content).build() }
            .concatWith(Flux.just(ServerSentEvent.builder<String>("[DONE]").build()))

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
