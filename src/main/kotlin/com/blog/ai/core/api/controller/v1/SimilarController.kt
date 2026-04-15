package com.blog.ai.core.api.controller.v1

import com.blog.ai.core.domain.similar.SimilarService
import com.blog.ai.core.support.response.ApiResponse
import com.blog.ai.core.api.controller.v1.request.SimilarRequest
import com.blog.ai.core.api.controller.v1.response.SimilarResponse
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
    fun findSimilar(@RequestBody request: SimilarRequest): ApiResponse<List<SimilarResponse>> {
        val articles = similarService.findSimilar(request.vector)
        val response = articles.map { article ->
            SimilarResponse(
                id = article.id,
                title = article.title,
                url = article.url,
                company = article.company,
                score = article.score,
            )
        }
        return ApiResponse.success(response)
    }
}
