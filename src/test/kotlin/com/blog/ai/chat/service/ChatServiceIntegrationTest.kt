package com.blog.ai.chat.service

import com.blog.ai.chat.repository.RateLimitStore
import com.blog.ai.support.PostgresTestContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import reactor.core.publisher.Flux
import java.util.function.Consumer

@SpringBootTest
@Import(PostgresTestContainer::class)
class ChatServiceIntegrationTest
    @Autowired
    constructor(
        private val chatService: ChatService,
        private val rateLimitStore: RateLimitStore,
        private val jdbcTemplate: JdbcTemplate,
    ) {
        @MockitoBean
        private lateinit var chatClient: ChatClient

        @MockitoBean
        private lateinit var chatQueryPlanner: QueryPlanner

        private lateinit var requestSpec: ChatClient.ChatClientRequestSpec
        private lateinit var streamSpec: ChatClient.StreamResponseSpec

        @BeforeEach
        fun reset() {
            jdbcTemplate.update("TRUNCATE TABLE chat_messages, chat_sessions RESTART IDENTITY")
            jdbcTemplate.update("TRUNCATE TABLE chat_rate_limit")

            requestSpec = Mockito.mock(ChatClient.ChatClientRequestSpec::class.java)
            streamSpec = Mockito.mock(ChatClient.StreamResponseSpec::class.java)

            Mockito.`when`(chatClient.prompt()).thenReturn(requestSpec)
            Mockito.`when`(requestSpec.user(anyString())).thenReturn(requestSpec)
            Mockito
                .`when`(requestSpec.advisors(any<Consumer<ChatClient.AdvisorSpec>>()))
                .thenReturn(requestSpec)
            Mockito.`when`(requestSpec.stream()).thenReturn(streamSpec)
            Mockito.`when`(streamSpec.content()).thenReturn(Flux.just("안녕하세요"))

            Mockito.`when`(chatQueryPlanner.plan(anyString(), anyString())).thenAnswer { inv ->
                QueryPlanner.Plan(
                    intent = QueryPlanner.Intent.GENERAL,
                    rewrittenQuery = inv.getArgument<String>(1),
                )
            }
        }

        @Test
        fun `chat consumes rate limit in writable transaction`() {
            val sessionId = chatService.createSession()

            val events = chatService.chat(sessionId, "안녕", "127.0.0.1").collectList().block()

            assertEquals(listOf("안녕하세요", "[DONE]"), events?.map { it.data() })
            assertEquals(
                1,
                rateLimitStore.getActiveCount(RateLimitStore.SCOPE_SESSION, sessionId.toString()),
            )
            assertEquals(1, rateLimitStore.getActiveCount(RateLimitStore.SCOPE_IP_HOUR, "127.0.0.1"))
        }
    }
