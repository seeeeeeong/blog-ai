package com.blog.ai.chat.application.retrieval

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ClarificationGuardTest {
    private val guard = ClarificationGuard()

    @Test
    fun `consume returns false when nothing was marked`() {
        val sessionId = UUID.randomUUID()

        assertFalse(guard.consume(sessionId))
    }

    @Test
    fun `consume returns true once after mark, then false`() {
        val sessionId = UUID.randomUUID()

        guard.mark(sessionId)

        assertTrue(guard.consume(sessionId))
        assertFalse(guard.consume(sessionId))
    }

    @Test
    fun `mark and consume are scoped per session`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()

        guard.mark(first)

        assertFalse(guard.consume(second))
        assertTrue(guard.consume(first))
    }
}
