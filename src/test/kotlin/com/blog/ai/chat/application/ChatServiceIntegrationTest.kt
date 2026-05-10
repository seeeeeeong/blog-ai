package com.blog.ai.chat.application

import com.blog.ai.chat.application.retrieval.ClarificationService
import com.blog.ai.chat.application.retrieval.QueryPlanner
import com.blog.ai.chat.application.session.ChatSessionService
import com.blog.ai.chat.infrastructure.ratelimit.RateLimitStore
import com.blog.ai.support.PostgresTestContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.UserMessage
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
        private val chatSessionService: ChatSessionService,
        private val rateLimitStore: RateLimitStore,
        private val jdbcTemplate: JdbcTemplate,
        private val chatMemory: ChatMemory,
    ) {
        @MockitoBean
        private lateinit var chatClient: ChatClient

        @MockitoBean
        private lateinit var chatQueryPlanner: QueryPlanner

        @MockitoBean
        private lateinit var clarificationService: ClarificationService

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

            Mockito.`when`(clarificationService.clarify(anyString(), anyString(), any())).thenAnswer { inv ->
                val sessionKey = inv.getArgument<String>(0)
                val question = inv.getArgument<String>(1)
                chatMemory.add(sessionKey, listOf(UserMessage(question), AssistantMessage(CLARIFY_TEXT)))
                CLARIFY_TEXT
            }
        }

        @Test
        fun `guard forces GENERAL when planner returns CLARIFY two turns in a row`() {
            val sessionId = chatSessionService.createSession().id
            stubPlannerWithIntent(QueryPlanner.Intent.CLARIFY)

            val firstTurn = chatService.chat(sessionId, AMBIGUOUS_QUESTION, "127.0.0.1").collectList().block()
            val secondTurn = chatService.chat(sessionId, CLARIFY_ANSWER, "127.0.0.1").collectList().block()

            assertEquals(listOf(CLARIFY_TEXT, "[DONE]"), firstTurn?.map { it.data() })
            assertEquals(listOf("안녕하세요", "[DONE]"), secondTurn?.map { it.data() })
            Mockito
                .verify(clarificationService, Mockito.times(1))
                .clarify(anyString(), anyString(), any())
            assertTrue(
                Mockito.mockingDetails(requestSpec).invocations.any {
                    it.method.name == "stream"
                },
                "second turn should reach the streaming chat path, not clarify again",
            )
        }

        @Test
        fun `guard does not leak across an intervening non-CLARIFY turn`() {
            val sessionId = chatSessionService.createSession().id
            val intentByCallIndex =
                mutableListOf(
                    QueryPlanner.Intent.CLARIFY,
                    QueryPlanner.Intent.GENERAL,
                    QueryPlanner.Intent.CLARIFY,
                )
            Mockito.`when`(chatQueryPlanner.plan(anyString(), anyString())).thenAnswer { inv ->
                QueryPlanner.Plan(
                    intent = intentByCallIndex.removeFirst(),
                    rewrittenQuery = inv.getArgument<String>(1),
                    clarificationQuestion = "특정 글 기준인가요?",
                )
            }

            val firstTurn = chatService.chat(sessionId, AMBIGUOUS_QUESTION, "127.0.0.1").collectList().block()
            val secondTurn = chatService.chat(sessionId, CLARIFY_ANSWER, "127.0.0.1").collectList().block()
            val thirdTurn = chatService.chat(sessionId, "또 다른 모호한 질문", "127.0.0.1").collectList().block()

            assertEquals(listOf(CLARIFY_TEXT, "[DONE]"), firstTurn?.map { it.data() })
            assertEquals(listOf("안녕하세요", "[DONE]"), secondTurn?.map { it.data() })
            assertEquals(
                listOf(CLARIFY_TEXT, "[DONE]"),
                thirdTurn?.map { it.data() },
                "third turn must surface a real clarification — stale flag from turn 1 must not hijack it",
            )
            Mockito
                .verify(clarificationService, Mockito.times(2))
                .clarify(anyString(), anyString(), any())
        }

        @Test
        fun `chat consumes rate limit in writable transaction`() {
            val sessionId = chatSessionService.createSession().id

            val events = chatService.chat(sessionId, "안녕", "127.0.0.1").collectList().block()

            assertEquals(listOf("안녕하세요", "[DONE]"), events?.map { it.data() })
            assertEquals(
                1,
                rateLimitStore.getActiveCount(RateLimitStore.SCOPE_SESSION, sessionId.toString()),
            )
            assertEquals(1, rateLimitStore.getActiveCount(RateLimitStore.SCOPE_IP_DAY, "127.0.0.1"))
        }

        private fun stubPlannerWithIntent(intent: QueryPlanner.Intent) {
            Mockito.`when`(chatQueryPlanner.plan(anyString(), anyString())).thenAnswer { inv ->
                QueryPlanner.Plan(
                    intent = intent,
                    rewrittenQuery = inv.getArgument<String>(1),
                    clarificationQuestion = "특정 글 기준인가요?",
                )
            }
        }

        companion object {
            private const val AMBIGUOUS_QUESTION = "이미지 유사도 추천 구현한 블로그 글 있어?"
            private const val CLARIFY_ANSWER = "비슷한 글을 찾고 있어"
            private const val CLARIFY_TEXT = "특정 글 기준인가요?"
        }
    }
