package com.blog.ai.chat.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.memory.ChatMemory

class QueryPlannerTest {
    private val planner =
        QueryPlanner(
            chatClientBuilder = Mockito.mock(ChatClient.Builder::class.java),
            chatMemory = Mockito.mock(ChatMemory::class.java),
            objectMapper = ObjectMapper(),
        )

    private val fallback = "fallback question"

    @Test
    fun `parses DESIGN intent with rewritten canonical query`() {
        val raw = """{"intent":"DESIGN","rewrittenQuery":"RAG 기반 관련 게시글 추천 시스템 설계"}"""

        val plan = planner.parsePlan(raw, fallback)

        assertEquals(QueryPlanner.Intent.DESIGN, plan.intent)
        assertEquals("RAG 기반 관련 게시글 추천 시스템 설계", plan.rewrittenQuery)
    }

    @Test
    fun `parses CLARIFY intent with verbatim echo`() {
        val raw = """{"intent":"CLARIFY","rewrittenQuery":"비슷한 글 추천해줘"}"""

        val plan = planner.parsePlan(raw, fallback)

        assertEquals(QueryPlanner.Intent.CLARIFY, plan.intent)
        assertEquals("비슷한 글 추천해줘", plan.rewrittenQuery)
    }

    @Test
    fun `parses GENERAL intent with resolved pronoun query`() {
        val raw = """{"intent":"GENERAL","rewrittenQuery":"pgvector HNSW latency"}"""

        val plan = planner.parsePlan(raw, fallback)

        assertEquals(QueryPlanner.Intent.GENERAL, plan.intent)
        assertEquals("pgvector HNSW latency", plan.rewrittenQuery)
    }

    @Test
    fun `strips markdown fence around JSON before parsing`() {
        val raw = "```json\n{\"intent\":\"DESIGN\",\"rewrittenQuery\":\"X\"}\n```"

        val plan = planner.parsePlan(raw, fallback)

        assertEquals(QueryPlanner.Intent.DESIGN, plan.intent)
        assertEquals("X", plan.rewrittenQuery)
    }

    @Test
    fun `falls back to GENERAL with original question when intent is unknown`() {
        val raw = """{"intent":"UNKNOWN","rewrittenQuery":"X"}"""

        val plan = planner.parsePlan(raw, fallback)

        assertEquals(QueryPlanner.Intent.GENERAL, plan.intent)
        assertEquals(fallback, plan.rewrittenQuery)
    }

    @Test
    fun `falls back to original question when rewrittenQuery is missing or blank`() {
        val raw = """{"intent":"DESIGN","rewrittenQuery":""}"""

        val plan = planner.parsePlan(raw, fallback)

        assertEquals(QueryPlanner.Intent.DESIGN, plan.intent)
        assertEquals(fallback, plan.rewrittenQuery)
    }

    @Test
    fun `parses clarificationQuestion when CLARIFY`() {
        val raw =
            "{\"intent\":\"CLARIFY\"," +
                "\"rewrittenQuery\":\"비슷한 글 추천해줘\"," +
                "\"clarificationQuestion\":\"특정 글 기준인가요, 기능 설계인가요?\"}"

        val plan = planner.parsePlan(raw, fallback)

        assertEquals(QueryPlanner.Intent.CLARIFY, plan.intent)
        assertEquals("특정 글 기준인가요, 기능 설계인가요?", plan.clarificationQuestion)
    }

    @Test
    fun `clarificationQuestion is null when omitted or null`() {
        val rawNullField = "{\"intent\":\"GENERAL\",\"rewrittenQuery\":\"X\",\"clarificationQuestion\":null}"
        val rawOmitted = "{\"intent\":\"GENERAL\",\"rewrittenQuery\":\"X\"}"

        assertEquals(null, planner.parsePlan(rawNullField, fallback).clarificationQuestion)
        assertEquals(null, planner.parsePlan(rawOmitted, fallback).clarificationQuestion)
    }

    @Test
    fun `more results patterns match deterministic guard cases`() {
        val cases =
            listOf(
                "저 두 기술 블로그 말고 다른 기술블로그는 없어? 챗봇 관련?",
                "다른 자료 없어?",
                "또 없어?",
                "더 보여줘",
                "더 알려줘",
                "더 있어?",
            )

        cases.forEach { q ->
            assertEquals(true, planner.matchesMoreResults(q), "should match: '$q'")
        }
    }

    @Test
    fun `more results patterns do not match unrelated questions`() {
        val cases =
            listOf(
                "RAG 기반 추천 시스템 설계 어떻게 해?",
                "비슷한 글 추천해줘",
                "관련 게시글 추천 이런거",
                "pgvector HNSW 옵션은?",
            )

        cases.forEach { q ->
            assertEquals(false, planner.matchesMoreResults(q), "should NOT match: '$q'")
        }
    }
}
