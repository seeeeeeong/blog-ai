package com.blog.ai.global.error

import org.springframework.boot.logging.LogLevel
import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val code: String,
    val message: String,
    val logLevel: LogLevel = LogLevel.INFO,
) {
    // ==================== Auth ====================
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_001", "Unauthorized"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_002", "Forbidden", LogLevel.WARN),

    // ==================== Common ====================
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "Invalid input"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_002", "Internal server error", LogLevel.ERROR),
    DEFAULT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_999", "An unexpected error occurred", LogLevel.ERROR),

    // ==================== Article ====================
    ARTICLE_NOT_FOUND(HttpStatus.NOT_FOUND, "ARTICLE_001", "Article not found", LogLevel.WARN),

    // ==================== Blog ====================
    BLOG_NOT_FOUND(HttpStatus.NOT_FOUND, "BLOG_001", "Blog not found", LogLevel.WARN),

    // ==================== Chat ====================
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_001", "Chat session not found", LogLevel.WARN),
    CHAT_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "CHAT_002", "Chat rate limit exceeded"),
    INVALID_FEEDBACK(HttpStatus.BAD_REQUEST, "CHAT_003", "Invalid feedback rating"),

    // ==================== Embed ====================
    EMBED_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "EMBED_001", "Embedding failed", LogLevel.ERROR),

    // ==================== Crawl ====================
    CRAWL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "CRAWL_001", "Crawl failed", LogLevel.ERROR),

    // ==================== BlogPost ====================
    BLOG_POST_INVALID_PAYLOAD(HttpStatus.BAD_REQUEST, "POST_001", "Invalid blog post payload"),
}
