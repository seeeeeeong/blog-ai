package com.blog.ai.core.api.controller.v1

import com.blog.ai.core.domain.article.ArticleAdminService
import com.blog.ai.core.domain.article.ArticleEmbedService
import com.blog.ai.core.domain.crawl.CrawlAsyncService
import com.blog.ai.core.domain.post.BlogPostEmbedService
import com.blog.ai.global.error.AppException
import com.blog.ai.global.error.ErrorCode
import com.blog.ai.global.properties.AdminProperties
import com.blog.ai.global.properties.InternalProperties
import com.blog.ai.global.response.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin")
class AdminController(
    private val adminProperties: AdminProperties,
    private val crawlAsyncService: CrawlAsyncService,
    private val articleEmbedService: ArticleEmbedService,
    private val articleAdminService: ArticleAdminService,
    private val blogPostEmbedService: BlogPostEmbedService,
    private val internalProperties: InternalProperties,
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

    @PostMapping("/blog-posts/embed")
    fun triggerBlogPostEmbed(
        @RequestHeader("X-Admin-Key") adminKey: String,
    ): ApiResponse<Int> {
        requireAdminKey(adminKey)
        blogPostEmbedService.clearRetriableErrors()
        val count = blogPostEmbedService.embedPending()
        return ApiResponse.success(count)
    }

    @PostMapping("/articles/re-embed")
    fun triggerArticleReembed(
        @RequestHeader("X-Admin-Key") adminKey: String,
    ): ApiResponse<Int> {
        requireAdminKey(adminKey)
        val pended = articleAdminService.markAllForReembed()
        return ApiResponse.success(pended)
    }

    private fun requireAdminKey(key: String) {
        if (key == adminProperties.apiKey) return
        if (key == internalProperties.apiKey) return
        throw AppException(ErrorCode.UNAUTHORIZED)
    }
}
