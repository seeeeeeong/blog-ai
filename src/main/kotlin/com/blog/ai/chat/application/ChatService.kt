package com.blog.ai.chat.application

import com.blog.ai.chat.application.ratelimit.ChatPreflight
import com.blog.ai.chat.application.ratelimit.RateLimiter
import com.blog.ai.chat.application.retrieval.ArticleRetriever
import com.blog.ai.chat.application.retrieval.ClarificationService
import com.blog.ai.chat.application.retrieval.QueryPlanner
import com.blog.ai.chat.domain.ChatAdvisorParams
import org.springframework.ai.chat.client.ChatClient
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import java.util.UUID

@Service
class ChatService(
    private val chatClient: ChatClient,
    private val chatRateLimiter: RateLimiter,
    private val chatPreflight: ChatPreflight,
    private val chatQueryPlanner: QueryPlanner,
    private val clarificationService: ClarificationService,
) {
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
    ): Flux<ServerSentEvent<String>> =
        chatClient
            .prompt()
            .user(question)
            .advisors { advisor ->
                advisor.param("chat_memory_conversation_id", sessionId.toString())
                advisor.param(ChatAdvisorParams.REWRITTEN_QUERY, rewrittenQuery)
                advisor.param(ArticleRetriever.INTENT_PARAM, intent.name)
            }.stream()
            .content()
            .map { content -> ServerSentEvent.builder(content).build() }
            .concatWith(Flux.just(ServerSentEvent.builder<String>("[DONE]").build()))
}
