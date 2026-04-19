package com.blog.ai.core.api.controller.v1

import com.blog.ai.core.api.controller.v1.response.ArticleAdminResponse
import com.blog.ai.core.api.controller.v1.response.SimilarDiagnoseResponse
import com.blog.ai.core.domain.article.ArticleAdminService
import com.blog.ai.core.domain.article.ArticleEmbedService
import com.blog.ai.core.domain.crawl.CrawlAsyncService
import com.blog.ai.core.domain.post.BlogPostEmbedService
import com.blog.ai.core.domain.post.BlogPostSimilarService
import com.blog.ai.core.support.error.CoreException
import com.blog.ai.core.support.error.ErrorType
import com.blog.ai.core.support.properties.AdminProperties
import com.blog.ai.core.support.response.ApiResponse
import com.blog.ai.core.support.response.PageResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin")
class AdminController(
    private val adminProperties: AdminProperties,
    private val crawlAsyncService: CrawlAsyncService,
    private val articleEmbedService: ArticleEmbedService,
    private val articleAdminService: ArticleAdminService,
    private val blogPostEmbedService: BlogPostEmbedService,
    private val blogPostSimilarService: BlogPostSimilarService,
) {
    @PostMapping("/crawl")
    fun triggerCrawl(
        @RequestHeader("X-Admin-Key") adminKey: String,
    ): ApiResponse<String> {
        requireAdminKey(adminKey)
        crawlAsyncService.crawlAsync()
        return ApiResponse.success("Crawl started")
    }

    @PostMapping("/embed")
    fun triggerEmbed(
        @RequestHeader("X-Admin-Key") adminKey: String,
    ): ApiResponse<Int> {
        requireAdminKey(adminKey)
        val count = articleEmbedService.embedPending()
        return ApiResponse.success(count)
    }

    @PostMapping("/embed/retry")
    fun retryEmbed(
        @RequestHeader("X-Admin-Key") adminKey: String,
    ): ApiResponse<Int> {
        requireAdminKey(adminKey)
        articleEmbedService.clearRetriableErrors()
        val count = articleEmbedService.embedPending()
        return ApiResponse.success(count)
    }

    @PostMapping("/blog-posts/embed")
    fun triggerBlogPostEmbed(
        @RequestHeader("X-Admin-Key") adminKey: String,
    ): ApiResponse<Int> {
        requireAdminKey(adminKey)
        blogPostEmbedService.clearRetriableErrors()
        val count = blogPostEmbedService.embedPending()
        return ApiResponse.success(count)
    }

    @GetMapping("/articles")
    fun getArticles(
        @RequestHeader("X-Admin-Key") adminKey: String,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
    ): ApiResponse<PageResponse<ArticleAdminResponse>> {
        requireAdminKey(adminKey)
        val articles = articleAdminService.findArticlesForAdmin(limit + 1, offset)
        val hasNext = articles.size > limit
        val response = articles.take(limit).map(ArticleAdminResponse.Companion::of)
        return ApiResponse.success(PageResponse(response, hasNext))
    }

    @GetMapping("/similar/diagnose")
    fun diagnoseSimilar(
        @RequestHeader("X-Admin-Key") adminKey: String,
        @RequestParam postId: String,
        @RequestParam(defaultValue = "10") limit: Int,
    ): ApiResponse<SimilarDiagnoseResponse> {
        requireAdminKey(adminKey)
        val result = blogPostSimilarService.diagnose(postId, limit)
        return ApiResponse.success(SimilarDiagnoseResponse.of(result))
    }

    @GetMapping("/stats")
    fun getStats(
        @RequestHeader("X-Admin-Key") adminKey: String,
    ): ApiResponse<Map<String, Any>> {
        requireAdminKey(adminKey)
        return ApiResponse.success(
            mapOf(
                "totalArticles" to articleAdminService.countTotal(),
                "unembedded" to articleAdminService.countUnembedded(),
            ),
        )
    }

    private fun requireAdminKey(key: String) {
        if (key == adminProperties.apiKey) return
        throw CoreException(ErrorType.UNAUTHORIZED)
    }
}
