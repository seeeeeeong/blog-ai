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

            CLASSIFICATION PRIORITY:

            Step 1. Detect correction/refinement signals in the latest message:
                "아니", "그게 아니라", "내 말은", "이런거", "같은 거", "그런 종류".
                If any are present, the user is CONTINUING the prior topic with a
                new constraint. Do NOT classify based on the latest message alone —
                use the prior turn's topic as the anchor.

            Step 2. If the prior topic (from history) is about engineering,
                architecture, design, RAG, embedding, recommendation system, or
                similar tech-build questions, AND the latest message refines that
                topic, classify as DESIGN with rewrittenQuery merging both.

            Step 3. Only after Step 1/2 fail to anchor a topic, fall through to
                the intent definitions below.

            Intent definitions:

            - DESIGN  — user is asking how to BUILD/DESIGN a "관련 게시글 추천 /
                        related post recommendation / 비슷한 글 추천" feature, OR
                        a previous turn already established a design / engineering
                        topic ("RAG", "임베딩", "추천 시스템 설계", "구현",
                        "어떻게 만들어") and the latest message refines it.
                        rewrittenQuery → canonical search merging the prior topic
                        with the latest constraint, e.g.
                          "RAG 기반 관련 게시글 추천 시스템 설계"

            - CLARIFY — user mentions "관련 게시글 추천 / 비슷한 글" with NO prior
                        context that resolves design vs execute. Use this only
                        when the conversation gives no engineering/design signal.
                        Example (no prior context): "비슷한 글 추천해줘".
                        rewrittenQuery → echo the latest message verbatim
                        (we will not search anyway).

            - GENERAL — every other tech question. rewrittenQuery is a standalone
                        search query, with pronouns and correction signals
                        resolved against history.

            Examples:

              History: (empty)
              Latest:  "비슷한 글 추천해줘"
              → {"intent":"CLARIFY","rewrittenQuery":"비슷한 글 추천해줘"}

              History: user: RAG 기반 추천시스템 설계 어떻게해?
              Latest:  "아니 관련 게시글 추천 이런거"
              → {"intent":"DESIGN","rewrittenQuery":"RAG 기반 관련 게시글 추천 시스템 설계"}

              History: user: pgvector HNSW 옵션은? assistant: m=16, ef=64...
              Latest:  "그거 latency는?"
              → {"intent":"GENERAL","rewrittenQuery":"pgvector HNSW latency"}

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

    internal fun parsePlan(
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
