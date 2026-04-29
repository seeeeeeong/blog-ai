package com.blog.ai.chat.application.retrieval

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.core.io.ByteArrayResource

class QueryPlannerTest {
    private val planner =
        QueryPlanner(
            chatClientBuilder = Mockito.mock(ChatClient.Builder::class.java),
            chatMemory = Mockito.mock(ChatMemory::class.java),
            plannerPromptResource = ByteArrayResource(ByteArray(0)),
        )

    private val fallback = "fallback question"

    @Test
    fun `maps DESIGN intent with rewritten canonical query`() {
        val output =
            QueryPlanner.PlannerOutput(
                intent = "DESIGN",
                rewrittenQuery = "RAG 기반 관련 게시글 추천 시스템 설계",
            )

        val plan = planner.toPlan(output, fallback)

        assertEquals(QueryPlanner.Intent.DESIGN, plan.intent)
        assertEquals("RAG 기반 관련 게시글 추천 시스템 설계", plan.rewrittenQuery)
    }

    @Test
    fun `maps CLARIFY intent with verbatim echo`() {
        val output =
            QueryPlanner.PlannerOutput(
                intent = "CLARIFY",
                rewrittenQuery = "비슷한 글 추천해줘",
            )

        val plan = planner.toPlan(output, fallback)

        assertEquals(QueryPlanner.Intent.CLARIFY, plan.intent)
        assertEquals("비슷한 글 추천해줘", plan.rewrittenQuery)
    }

    @Test
    fun `maps GENERAL intent with resolved pronoun query`() {
        val output =
            QueryPlanner.PlannerOutput(
                intent = "GENERAL",
                rewrittenQuery = "pgvector HNSW latency",
            )

        val plan = planner.toPlan(output, fallback)

        assertEquals(QueryPlanner.Intent.GENERAL, plan.intent)
        assertEquals("pgvector HNSW latency", plan.rewrittenQuery)
    }

    @Test
    fun `maps intent after trimming surrounding whitespace`() {
        val output =
            QueryPlanner.PlannerOutput(
                intent = " DESIGN ",
                rewrittenQuery = "RAG 기반 관련 게시글 추천 시스템 설계",
            )

        val plan = planner.toPlan(output, fallback)

        assertEquals(QueryPlanner.Intent.DESIGN, plan.intent)
        assertEquals("RAG 기반 관련 게시글 추천 시스템 설계", plan.rewrittenQuery)
    }

    @Test
    fun `falls back to GENERAL with original question when intent is unknown`() {
        val output =
            QueryPlanner.PlannerOutput(
                intent = "UNKNOWN",
                rewrittenQuery = "X",
            )

        val plan = planner.toPlan(output, fallback)

        assertEquals(QueryPlanner.Intent.GENERAL, plan.intent)
        assertEquals(fallback, plan.rewrittenQuery)
    }

    @Test
    fun `falls back to original question when rewrittenQuery is blank`() {
        val output =
            QueryPlanner.PlannerOutput(
                intent = "DESIGN",
                rewrittenQuery = "",
            )

        val plan = planner.toPlan(output, fallback)

        assertEquals(QueryPlanner.Intent.DESIGN, plan.intent)
        assertEquals(fallback, plan.rewrittenQuery)
    }

    @Test
    fun `parses clarificationQuestion when CLARIFY`() {
        val output =
            QueryPlanner.PlannerOutput(
                intent = "CLARIFY",
                rewrittenQuery = "비슷한 글 추천해줘",
                clarificationQuestion = "특정 글 기준인가요, 기능 설계인가요?",
            )

        val plan = planner.toPlan(output, fallback)

        assertEquals(QueryPlanner.Intent.CLARIFY, plan.intent)
        assertEquals("특정 글 기준인가요, 기능 설계인가요?", plan.clarificationQuestion)
    }

    @Test
    fun `clarificationQuestion is null when omitted, blank, or null`() {
        val nullField =
            QueryPlanner.PlannerOutput(
                intent = "GENERAL",
                rewrittenQuery = "X",
                clarificationQuestion = null,
            )
        val blankField =
            QueryPlanner.PlannerOutput(
                intent = "GENERAL",
                rewrittenQuery = "X",
                clarificationQuestion = "   ",
            )

        assertEquals(null, planner.toPlan(nullField, fallback).clarificationQuestion)
        assertEquals(null, planner.toPlan(blankField, fallback).clarificationQuestion)
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
