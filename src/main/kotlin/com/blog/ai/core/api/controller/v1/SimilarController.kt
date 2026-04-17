package com.blog.ai.core.api.controller.v1

import com.blog.ai.core.api.controller.v1.request.SimilarRequest
import com.blog.ai.core.api.controller.v1.response.SimilarResponse
import com.blog.ai.core.domain.similar.SimilarService
import com.blog.ai.core.support.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/similar")
class SimilarController(
    private val similarService: SimilarService,
) {

    @PostMapping
    fun findSimilar(@Valid @RequestBody request: SimilarRequest): ApiResponse<List<SimilarResponse>> {
        val articles = similarService.findSimilar(request.vector)
        return ApiResponse.success(articles.map(SimilarResponse.Companion::of))
    }
}
