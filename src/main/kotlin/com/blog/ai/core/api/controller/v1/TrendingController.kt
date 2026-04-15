package com.blog.ai.core.api.controller.v1

import com.blog.ai.core.api.controller.v1.response.TrendingResponse
import com.blog.ai.core.domain.trending.HnTrendingService
import com.blog.ai.core.support.response.ApiResponse
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
        return ApiResponse.success(items.map(TrendingResponse.Companion::of))
    }
}
