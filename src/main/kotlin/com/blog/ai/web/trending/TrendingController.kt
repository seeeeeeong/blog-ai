package com.blog.ai.web.trending

import com.blog.ai.core.domain.trending.HnTrendingService
import com.blog.ai.core.support.response.ApiResponse
import com.blog.ai.web.trending.dto.TrendingResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/trending")
class TrendingController(
    private val hnTrendingService: HnTrendingService,
) {

    @GetMapping
    fun getTrending(): ApiResponse<List<TrendingResponse>> {
        val items = hnTrendingService.getItems()
        val response = items.map { item ->
            TrendingResponse(
                title = item.title,
                url = item.url,
                score = item.score,
            )
        }
        return ApiResponse.success(response)
    }
}
