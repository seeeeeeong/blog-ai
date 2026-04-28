package com.blog.ai.chat.infrastructure.ratelimit

import com.blog.ai.chat.domain.RateLimitOutcome
import com.blog.ai.chat.domain.RateLimitRequest
import com.blog.ai.support.PostgresTestContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration

@SpringBootTest
@Import(PostgresTestContainer::class)
class RateLimitStoreIntegrationTest
    @Autowired
    constructor(
        private val repository: RateLimitStore,
        private val jdbcTemplate: JdbcTemplate,
    ) {
        @BeforeEach
        fun reset() {
            jdbcTemplate.update("TRUNCATE TABLE chat_rate_limit")
        }

        @Test
        fun `tryConsume allows under limit and bumps both counters`() {
            val outcome = repository.tryConsume(request("s1", "ip1", sessionMax = 3, ipMax = 3))

            assertEquals(RateLimitOutcome.OK, outcome)
            assertEquals(1, repository.getActiveCount(RateLimitStore.SCOPE_SESSION, "s1"))
            assertEquals(1, repository.getActiveCount(RateLimitStore.SCOPE_IP_HOUR, "ip1"))
        }

        @Test
        fun `session-limited rejection does not consume ip quota`() {
            repeat(3) { repository.tryConsume(request("s1", "ip1", sessionMax = 3, ipMax = 10)) }

            val outcome = repository.tryConsume(request("s1", "ip1", sessionMax = 3, ipMax = 10))

            assertEquals(RateLimitOutcome.SESSION_LIMITED, outcome)
            assertEquals(3, repository.getActiveCount(RateLimitStore.SCOPE_SESSION, "s1"))
            assertEquals(3, repository.getActiveCount(RateLimitStore.SCOPE_IP_HOUR, "ip1"))
        }

        @Test
        fun `ip-limited rejection does not consume session quota`() {
            repeat(3) { repository.tryConsume(request("sA", "ip1", sessionMax = 10, ipMax = 3)) }

            val outcome = repository.tryConsume(request("sB", "ip1", sessionMax = 10, ipMax = 3))

            assertEquals(RateLimitOutcome.IP_LIMITED, outcome)
            assertEquals(0, repository.getActiveCount(RateLimitStore.SCOPE_SESSION, "sB"))
            assertEquals(3, repository.getActiveCount(RateLimitStore.SCOPE_IP_HOUR, "ip1"))
        }

        @Test
        fun `expired window resets count on next consume`() {
            repository.tryConsume(request("s1", "ip1", sessionMax = 3, ipMax = 3))
            jdbcTemplate.update("UPDATE chat_rate_limit SET reset_at = NOW() - INTERVAL '1 second'")

            val outcome = repository.tryConsume(request("s1", "ip1", sessionMax = 3, ipMax = 3))

            assertEquals(RateLimitOutcome.OK, outcome)
            assertEquals(1, repository.getActiveCount(RateLimitStore.SCOPE_SESSION, "s1"))
        }

        @Test
        fun `deleteExpired removes only expired rows`() {
            repository.tryConsume(request("sActive", "ipActive", sessionMax = 3, ipMax = 3))
            repository.tryConsume(request("sStale", "ipStale", sessionMax = 3, ipMax = 3))
            jdbcTemplate.update(
                """
                UPDATE chat_rate_limit
                   SET reset_at = NOW() - INTERVAL '1 second'
                 WHERE key IN ('sStale', 'ipStale')
                """.trimIndent(),
            )

            val deleted = repository.deleteExpired()

            assertEquals(2, deleted)
            assertEquals(1, repository.getActiveCount(RateLimitStore.SCOPE_SESSION, "sActive"))
        }

        private fun request(
            sessionKey: String,
            ipKey: String,
            sessionMax: Int,
            ipMax: Int,
        ) = RateLimitRequest(
            sessionKey = sessionKey,
            ipKey = ipKey,
            sessionMax = sessionMax,
            ipMax = ipMax,
            sessionTtl = Duration.ofHours(24),
            ipTtl = Duration.ofHours(1),
        )
    }
