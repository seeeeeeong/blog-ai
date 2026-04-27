package com.blog.ai.core.domain.chat

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.memory.ChatMemory

class ChatQueryPlannerTest {
    private val planner =
        ChatQueryPlanner(
            chatClientBuilder = Mockito.mock(ChatClient.Builder::class.java),
            chatMemory = Mockito.mock(ChatMemory::class.java),
            objectMapper = ObjectMapper(),
        )

    private val fallback = "fallback question"

    @Test
    fun `parses DESIGN intent with rewritten canonical query`() {
        val raw = """{"intent":"DESIGN","rewrittenQuery":"RAG 기반 관련 게시글 추천 시스템 설계"}"""

        val plan = planner.parsePlan(raw, fallback)

        assertEquals(ChatQueryPlanner.Intent.DESIGN, plan.intent)
        assertEquals("RAG 기반 관련 게시글 추천 시스템 설계", plan.rewrittenQuery)
    }

    @Test
    fun `parses CLARIFY intent with verbatim echo`() {
        val raw = """{"intent":"CLARIFY","rewrittenQuery":"비슷한 글 추천해줘"}"""

        val plan = planner.parsePlan(raw, fallback)

        assertEquals(ChatQueryPlanner.Intent.CLARIFY, plan.intent)
        assertEquals("비슷한 글 추천해줘", plan.rewrittenQuery)
    }

    @Test
    fun `parses GENERAL intent with resolved pronoun query`() {
        val raw = """{"intent":"GENERAL","rewrittenQuery":"pgvector HNSW latency"}"""

        val plan = planner.parsePlan(raw, fallback)

        assertEquals(ChatQueryPlanner.Intent.GENERAL, plan.intent)
        assertEquals("pgvector HNSW latency", plan.rewrittenQuery)
    }

    @Test
    fun `strips markdown fence around JSON before parsing`() {
        val raw = "```json\n{\"intent\":\"DESIGN\",\"rewrittenQuery\":\"X\"}\n```"

        val plan = planner.parsePlan(raw, fallback)

        assertEquals(ChatQueryPlanner.Intent.DESIGN, plan.intent)
        assertEquals("X", plan.rewrittenQuery)
    }

    @Test
    fun `falls back to GENERAL with original question when intent is unknown`() {
        val raw = """{"intent":"UNKNOWN","rewrittenQuery":"X"}"""

        val plan = planner.parsePlan(raw, fallback)

        assertEquals(ChatQueryPlanner.Intent.GENERAL, plan.intent)
        assertEquals(fallback, plan.rewrittenQuery)
    }

    @Test
    fun `falls back to original question when rewrittenQuery is missing or blank`() {
        val raw = """{"intent":"DESIGN","rewrittenQuery":""}"""

        val plan = planner.parsePlan(raw, fallback)

        assertEquals(ChatQueryPlanner.Intent.DESIGN, plan.intent)
        assertEquals(fallback, plan.rewrittenQuery)
    }
}
