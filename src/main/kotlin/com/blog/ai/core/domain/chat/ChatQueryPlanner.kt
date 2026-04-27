package com.blog.ai.core.domain.chat

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.stereotype.Component

@Component
class ChatQueryPlanner(
    private val chatClientBuilder: ChatClient.Builder,
    private val chatMemory: ChatMemory,
    private val objectMapper: ObjectMapper,
) {
    enum class Intent {
        DESIGN,
        CLARIFY,
        GENERAL,
    }

    data class Plan(
        val intent: Intent,
        val rewrittenQuery: String,
    )

    companion object {
        private val log = KotlinLogging.logger {}
        private const val MAX_HISTORY = 6
        private const val MAX_HISTORY_SNIPPET = 200

        private val PLANNER_PROMPT =
            """
            You are a query planner for a Korean tech-blog chatbot.

            The chatbot has TWO behaviors:
            (1) general RAG over crawled tech-blog articles (default).
            (2) a separate "find similar posts" feature that REQUIRES a specific
                author-post id and is NOT triggered from chat alone.

            Given conversation history and the latest user message, output a single
            JSON object on one line:

              {"intent":"DESIGN"|"CLARIFY"|"GENERAL","rewrittenQuery":"..."}

            Intent definitions:

            - DESIGN  — user is asking how to BUILD/DESIGN a "관련 게시글 추천 /
                        related post recommendation / 비슷한 글 추천" feature.
                        Engineering/architecture question.
                        rewrittenQuery → fixed canonical search:
                          "RAG 기반 관련 게시글 추천 시스템 설계"

            - CLARIFY — user wants "관련 게시글 추천 / 비슷한 글" but it is unclear
                        whether they want to BUILD it (DESIGN) or USE it on a
                        specific post (cannot be done in chat without a post id).
                        Examples: "관련 게시글 추천 이런거", "비슷한 글 추천해줘"
                        with no reference post.
                        rewrittenQuery → echo the user message verbatim
                        (we will not search anyway).

            - GENERAL — every other tech question. rewrittenQuery is a standalone
                        search query, with pronouns and correction signals
                        resolved against history.

            Correction & refinement rules (apply to rewrittenQuery for ALL intents):
            - "아니", "그게 아니라", "내 말은" → keep the previous turn's topic and
              ADD the new constraint. Do not drop the original topic.
            - "이런거", "같은 거", "그런 종류" → keep the previous topic and append
              the new specifier as a refinement.
            - Pronouns ("그거", "아까 말한 것") → resolve from history.

            Output ONLY the JSON object — no markdown fence, no commentary.
            """.trimIndent()
    }

    fun plan(
        sessionId: String,
        question: String,
    ): Plan {
        val history = chatMemory.get(sessionId)
        val recentHistory =
            if (history.isEmpty()) {
                ""
            } else {
                history
                    .takeLast(MAX_HISTORY)
                    .joinToString("\n") { "${it.messageType.value}: ${it.text?.take(MAX_HISTORY_SNIPPET)}" }
            }

        return try {
            val raw =
                chatClientBuilder
                    .build()
                    .prompt()
                    .system(PLANNER_PROMPT)
                    .user(buildUserPrompt(recentHistory, question))
                    .call()
                    .content()
                    ?.trim()
                    ?: return fallback(question)
            parsePlan(raw, question)
        } catch (e: Exception) {
            log.warn(e) { "Query planning failed, falling back to GENERAL" }
            fallback(question)
        }
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

    private fun parsePlan(
        raw: String,
        fallbackQuery: String,
    ): Plan {
        val cleaned =
            raw
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
        val node = objectMapper.readTree(cleaned)
        val intentStr = node.get("intent")?.asText()?.uppercase() ?: return fallback(fallbackQuery)
        val intent = Intent.entries.firstOrNull { it.name == intentStr } ?: return fallback(fallbackQuery)
        val rewritten =
            node
                .get("rewrittenQuery")
                ?.asText()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: fallbackQuery
        log.debug { "Plan: intent=$intent, rewrittenQuery='$rewritten'" }
        return Plan(intent, rewritten)
    }

    private fun fallback(question: String): Plan = Plan(Intent.GENERAL, question)
}
