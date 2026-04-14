package com.blog.ai.web.chat

import com.blog.ai.core.domain.chat.ChatService
import com.blog.ai.core.support.response.ApiResponse
import com.blog.ai.web.chat.dto.ChatSessionResponse
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import java.util.UUID

@RestController
@RequestMapping("/api/v1/chat")
class ChatController(
    private val chatService: ChatService,
) {

    @GetMapping("/session")
    fun createSession(): ApiResponse<ChatSessionResponse> {
        val sessionId = chatService.createSession()
        return ApiResponse.success(ChatSessionResponse(sessionId))
    }

    @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun chat(
        @RequestParam question: String,
        @RequestParam sessionId: UUID,
    ): Flux<ServerSentEvent<String>> {
        return chatService.chat(sessionId, question)
    }
}
