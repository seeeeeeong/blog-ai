package com.blog.ai.core.api.controller.v1

import com.blog.ai.core.support.properties.AdminProperties
import com.blog.ai.core.domain.article.ArticleEmbedService
import com.blog.ai.core.domain.crawl.CrawlService
import com.blog.ai.core.support.error.CoreException
import com.blog.ai.core.support.error.ErrorType
import com.blog.ai.core.support.response.ApiResponse
import com.blog.ai.core.support.response.PageResponse
import com.blog.ai.storage.article.ArticleRepository
import com.blog.ai.core.api.controller.v1.response.ArticleAdminResponse
import org.springframework.scheduling.annotation.Async
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

@RestController
@RequestMapping("/api/v1/admin")
class AdminController(
    private val adminProperties: AdminProperties,
    private val crawlService: CrawlService,
    private val articleEmbedService: ArticleEmbedService,
    private val articleRepository: ArticleRepository,
) {

    @PostMapping("/crawl")
    fun triggerCrawl(@RequestHeader("X-Admin-Key") adminKey: String): ApiResponse<String> {
        validateAdminKey(adminKey)
        crawlAsync()
        return ApiResponse.success("Crawl started")
    }

    @Async
    fun crawlAsync() {
        crawlService.crawlAll()
        articleEmbedService.embedPending()
    }

    @PostMapping("/embed")
    fun triggerEmbed(@RequestHeader("X-Admin-Key") adminKey: String): ApiResponse<Int> {
        validateAdminKey(adminKey)
        val count = articleEmbedService.embedPending()
        return ApiResponse.success(count)
    }

    @PostMapping("/embed/retry")
    fun retryEmbed(@RequestHeader("X-Admin-Key") adminKey: String): ApiResponse<Int> {
        validateAdminKey(adminKey)
        articleEmbedService.clearRetriableErrors()
        val count = articleEmbedService.embedPending()
        return ApiResponse.success(count)
    }

    @GetMapping("/articles")
    fun getArticles(
        @RequestHeader("X-Admin-Key") adminKey: String,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
    ): ApiResponse<PageResponse<ArticleAdminResponse>> {
        validateAdminKey(adminKey)
        val rows = articleRepository.findArticlesForAdmin(limit + 1, offset)
        val hasNext = rows.size > limit
        val articles = rows.take(limit).map { row ->
            ArticleAdminResponse(
                id = (row[0] as Number).toLong(),
                title = row[1] as String,
                url = row[2] as String,
                urlHash = row[3] as String,
                company = row[4] as String,
                embedded = row[5] as Boolean,
                embedError = row[6] as? String,
                crawledAt = row[7] as OffsetDateTime,
            )
        }
        return ApiResponse.success(PageResponse(articles, hasNext))
    }

    @GetMapping("/stats")
    fun getStats(@RequestHeader("X-Admin-Key") adminKey: String): ApiResponse<Map<String, Any>> {
        validateAdminKey(adminKey)
        return ApiResponse.success(
            mapOf(
                "totalArticles" to articleRepository.count(),
                "unembedded" to articleRepository.countUnembedded(),
            ),
        )
    }

    private fun validateAdminKey(key: String) {
        if (key != adminProperties.apiKey) {
            throw CoreException(ErrorType.UNAUTHORIZED)
        }
    }
}
