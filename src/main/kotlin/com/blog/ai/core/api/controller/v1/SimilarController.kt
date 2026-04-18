package com.blog.ai.core.api.controller.v1

import com.blog.ai.core.api.controller.v1.response.SimilarResponse
import com.blog.ai.core.domain.post.BlogPostSimilarService
import com.blog.ai.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/similar")
class SimilarController(
    private val blogPostSimilarService: BlogPostSimilarService,
) {
    @GetMapping
    fun findSimilar(
        @RequestParam postId: String,
        @RequestParam(defaultValue = "10") limit: Int,
    ): ApiResponse<SimilarResponse> {
        val result = blogPostSimilarService.findSimilar(postId, limit)
        return ApiResponse.success(SimilarResponse.of(result))
    }
}
