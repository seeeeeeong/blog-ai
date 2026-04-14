package com.blog.ai.core.support.error

import org.springframework.http.HttpStatus

enum class ErrorType(
    val status: HttpStatus,
    val code: String,
    val message: String,
    val logLevel: LogLevel = LogLevel.INFO,
) {
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_001", "Unauthorized"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_002", "Forbidden"),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "Invalid input"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_002", "Internal server error", LogLevel.ERROR),
    DEFAULT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_999", "Unexpected error", LogLevel.ERROR),
    ARTICLE_NOT_FOUND(HttpStatus.NOT_FOUND, "ARTICLE_001", "Article not found"),
    BLOG_NOT_FOUND(HttpStatus.NOT_FOUND, "BLOG_001", "Blog not found"),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_001", "Chat session not found"),
    EMBED_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "EMBED_001", "Embedding failed", LogLevel.ERROR),
    CRAWL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "CRAWL_001", "Crawl failed", LogLevel.ERROR),
}

enum class LogLevel {
    INFO, WARN, ERROR
}
