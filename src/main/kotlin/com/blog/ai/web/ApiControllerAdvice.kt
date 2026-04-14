package com.blog.ai.web

import com.blog.ai.core.support.error.CoreException
import com.blog.ai.core.support.error.LogLevel
import com.blog.ai.core.support.response.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiControllerAdvice {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(CoreException::class)
    fun handleCoreException(e: CoreException): ResponseEntity<ApiResponse<Any>> {
        when (e.errorType.logLevel) {
            LogLevel.ERROR -> log.error("CoreException: {}", e.message)
            LogLevel.WARN -> log.warn("CoreException: {}", e.message)
            LogLevel.INFO -> log.info("CoreException: {}", e.message)
        }

        return ResponseEntity
            .status(e.errorType.status)
            .body(ApiResponse.error(e.errorType))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ApiResponse<Any>> {
        log.error("Unexpected error", e)
        return ResponseEntity
            .internalServerError()
            .body(ApiResponse.error(com.blog.ai.core.support.error.ErrorType.DEFAULT_ERROR))
    }
}
