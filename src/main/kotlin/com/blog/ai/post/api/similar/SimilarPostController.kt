package com.blog.ai.post.api.similar

import com.blog.ai.global.response.ApiResponse
import com.blog.ai.post.api.similar.SimilarResponse
import com.blog.ai.post.application.similar.SimilarPostService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/similar")
class SimilarPostController(
    private val similarPostService: SimilarPostService,
) {
    @GetMapping
    fun findSimilar(
        @RequestParam postId: String,
        @RequestParam(defaultValue = "10") limit: Int,
    ): ApiResponse<SimilarResponse> {
        val result = similarPostService.findSimilar(postId, limit)
        return ApiResponse.success(SimilarResponse.of(result))
    }
}
