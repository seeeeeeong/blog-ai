package com.blog.ai.core.api.controller.v1

import com.blog.ai.core.api.controller.v1.request.ChatRequest
import com.blog.ai.core.api.controller.v1.response.ChatMessageResponse
import com.blog.ai.core.api.controller.v1.response.ChatSessionResponse
import com.blog.ai.chat.ChatService
import com.blog.ai.global.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
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
        return ApiResponse.success(ChatSessionResponse.of(sessionId))
    }

    @PostMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun chat(
        @Valid @RequestBody request: ChatRequest,
        httpRequest: HttpServletRequest,
    ): Flux<ServerSentEvent<String>> {
        val clientIp = resolveClientIp(httpRequest)
        return chatService.chat(request.sessionId, request.question, clientIp)
    }

    @GetMapping("/messages")
    fun getMessages(
        @RequestParam sessionId: UUID,
    ): ApiResponse<List<ChatMessageResponse>> {
        val messages = chatService.getMessages(sessionId).map(ChatMessageResponse::of)
        return ApiResponse.success(messages)
    }

    @GetMapping("/remaining")
    fun remainingMessages(
        @RequestParam sessionId: UUID,
    ): ApiResponse<Map<String, Int>> {
        val remaining = chatService.remainingMessages(sessionId)
        return ApiResponse.success(mapOf("remaining" to remaining))
    }

    private fun resolveClientIp(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For") ?: return request.remoteAddr
        val firstIp = forwarded.split(",").first().trim()
        if (firstIp.isNotBlank()) return firstIp
        return request.remoteAddr
    }
}
