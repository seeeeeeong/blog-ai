package com.blog.ai.core.domain.chat

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.MessageType
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

        private val PLANNER_PROMPT =
            """
            You are a query planner for a Korean tech-blog chatbot.

            The chatbot has TWO behaviors:
            (1) general RAG over crawled tech-blog articles (default).
            (2) a separate "find similar posts" feature that REQUIRES a specific
                author-post id and is NOT triggered from chat alone.

            Given conversation history and the latest user message, output a single
            JSON object on one line:

              {
                "intent":"DESIGN"|"CLARIFY"|"GENERAL",
                "rewrittenQuery":"...",
                "clarificationQuestion":null|"short Korean clarification question"
              }

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

            - CLARIFY — user explicitly asks for "관련 게시글 추천", "비슷한 글 추천",
                        or "similar posts" with NO prior context that resolves
                        design vs execute. Use this only when the conversation
                        gives no engineering/design signal.
                        Example (no prior context): "비슷한 글 추천해줘".
                        rewrittenQuery → echo the latest message verbatim
                        (we will not search anyway).
                        clarificationQuestion → ask whether they want to design
                        the feature or get similar posts for a specific article.

                        Do NOT classify as CLARIFY just because the Korean word
                        "관련" appears. Phrases like "챗봇 관련", "RAG 관련",
                        "다른 기술블로그는 없어?", "다른 자료 있어?" are GENERAL
                        search refinements, not related-post recommendation.

            - GENERAL — every other tech question. rewrittenQuery is a standalone
                        search query, with pronouns and correction signals
                        resolved against history.

            Examples:

              History: (empty)
              Latest:  "비슷한 글 추천해줘"
              → {"intent":"CLARIFY","rewrittenQuery":"비슷한 글 추천해줘","clarificationQuestion":"특정 글을 기준으로 비슷한 글을 추천받고 싶으신가요, 아니면 관련 게시글 추천 기능을 설계하려는 건가요?"}

              History: user: RAG 기반 추천시스템 설계 어떻게해?
              Latest:  "아니 관련 게시글 추천 이런거"
              → {"intent":"DESIGN","rewrittenQuery":"RAG 기반 관련 게시글 추천 시스템 설계","clarificationQuestion":null}

              History: user: 챗봇 시스템 어떻게 구현해?
                       assistant: Vercel and Dev.to sources...
              Latest:  "저 두 기술 블로그 말고 다른 기술블로그는 없어? 챗봇 관련?"
              → {"intent":"GENERAL","rewrittenQuery":"챗봇 시스템 구현 기술 블로그 사례 Vercel Dev.to 제외","clarificationQuestion":null}

              History: user: pgvector HNSW 옵션은? assistant: m=16, ef=64...
              Latest:  "그거 latency는?"
              → {"intent":"GENERAL","rewrittenQuery":"pgvector HNSW latency","clarificationQuestion":null}

            Output ONLY the JSON object — no markdown fence, no commentary.
            """.trimIndent()
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
        val clarificationQuestion =
            node
                .get("clarificationQuestion")
                ?.takeUnless { it.isNull }
                ?.asText()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        log.debug { "Plan: intent=$intent, rewrittenQuery='$rewritten'" }
        return Plan(intent, rewritten, clarificationQuestion)
    }

    private fun fallback(question: String): Plan = Plan(Intent.GENERAL, question)
}
