package com.blog.ai.chat.application.retrieval

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.MessageType
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

@Component("chatQueryPlanner")
class QueryPlanner(
    private val chatClientBuilder: ChatClient.Builder,
    private val chatMemory: ChatMemory,
    @Value("classpath:prompts/retrieval/query-planner.st")
    plannerPromptResource: Resource,
) {
    private val plannerPrompt: String = plannerPromptResource.getContentAsString(StandardCharsets.UTF_8)

    enum class Intent {
        DESIGN,
        CLARIFY,
        GENERAL,
    }

    data class Plan(
        val intent: Intent,
        val rewrittenQuery: String,
        val clarificationQuestion: String? = null,
    )

    data class PlannerOutput(
        val intent: String = "",
        val rewrittenQuery: String = "",
        val clarificationQuestion: String? = null,
    )

    companion object {
        private val log = KotlinLogging.logger {}
        private const val MAX_HISTORY = 6
        private const val MAX_HISTORY_SNIPPET = 200

        private val MORE_RESULTS_PATTERNS =
            listOf(
                Regex("말고\\s*다른"),
                Regex("더\\s*있"),
                Regex("더\\s*보여"),
                Regex("더\\s*알려"),
                Regex("다른\\s*\\S+.*없"),
                Regex("또\\s*없"),
            )
    }

    fun plan(
        sessionId: String,
        question: String,
    ): Plan {
        val history = chatMemory.get(sessionId)

        if (matchesMoreResults(question)) {
            val merged = mergedMoreResultsQuery(history, question)
            log.debug { "Plan(deterministic): intent=GENERAL, rewrittenQuery='$merged'" }
            return Plan(Intent.GENERAL, merged)
        }

        val recentHistory =
            if (history.isEmpty()) {
                ""
            } else {
                history
                    .takeLast(MAX_HISTORY)
                    .joinToString("\n") { "${it.messageType.value}: ${it.text?.take(MAX_HISTORY_SNIPPET)}" }
            }

        return try {
            val output =
                chatClientBuilder
                    .build()
                    .prompt()
                    .system(plannerPrompt)
                    .user(buildUserPrompt(recentHistory, question))
                    .call()
                    .entity(PlannerOutput::class.java)
                    ?: return fallback(question)
            toPlan(output, question)
        } catch (e: Exception) {
            log.warn(e) { "Query planning failed, falling back to GENERAL" }
            fallback(question)
        }
    }

    internal fun matchesMoreResults(question: String): Boolean =
        MORE_RESULTS_PATTERNS.any { it.containsMatchIn(question) }

    private fun mergedMoreResultsQuery(
        history: List<Message>,
        question: String,
    ): String {
        val priorUserText =
            history
                .lastOrNull { it.messageType == MessageType.USER }
                ?.text
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        if (priorUserText == null) return question
        return "$priorUserText $question"
    }

    private fun buildUserPrompt(
        history: String,
        question: String,
    ): String =
        if (history.isBlank()) {
            "Latest message: $question"
        } else {
            "Conversation history:\n$history\n\nLatest message: $question"
        }

    internal fun toPlan(
        output: PlannerOutput,
        fallbackQuery: String,
    ): Plan {
        val intent =
            Intent.entries.firstOrNull { it.name == output.intent.trim().uppercase() }
                ?: return fallback(fallbackQuery)
        val rewritten =
            output.rewrittenQuery
                .trim()
                .takeIf { it.isNotBlank() }
                ?: fallbackQuery
        val clarificationQuestion =
            output.clarificationQuestion
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        log.debug { "Plan: intent=$intent, rewrittenQuery='$rewritten'" }
        return Plan(intent, rewritten, clarificationQuestion)
    }

    private fun fallback(question: String): Plan = Plan(Intent.GENERAL, question)
}
