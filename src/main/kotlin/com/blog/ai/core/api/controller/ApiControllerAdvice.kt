package com.blog.ai.core.api.controller

import com.blog.ai.global.error.AppException
import com.blog.ai.global.error.ErrorCode
import com.blog.ai.global.response.ApiResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.ConstraintViolationException
import org.springframework.boot.logging.LogLevel
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BindException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class ApiControllerAdvice {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    @ExceptionHandler(AppException::class)
    fun handleCoreException(e: AppException): ResponseEntity<ApiResponse<Any>> {
        when (e.errorType.logLevel) {
            LogLevel.ERROR -> log.error(e) { "AppException: ${e.message}" }
            LogLevel.WARN -> log.warn { "AppException: ${e.message}" }
            else -> log.info { "AppException: ${e.message}" }
        }
        return ResponseEntity(ApiResponse.error(e.errorType, e.data, e.message), e.errorType.status)
    }

    @ExceptionHandler(
        ConstraintViolationException::class,
        MethodArgumentTypeMismatchException::class,
        MethodArgumentNotValidException::class,
        MissingServletRequestParameterException::class,
        HttpMessageNotReadableException::class,
        BindException::class,
        org.springframework.web.bind.ServletRequestBindingException::class,
    )
    fun handleBadRequestException(e: Exception): ResponseEntity<ApiResponse<Any>> {
        log.info { "Bad request: ${e.message}" }
        return ResponseEntity(ApiResponse.error(ErrorCode.INVALID_INPUT), ErrorCode.INVALID_INPUT.status)
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ApiResponse<Any>> {
        log.error(e) { "Unexpected exception occurred" }
        return ResponseEntity(ApiResponse.error(ErrorCode.DEFAULT_ERROR), ErrorCode.DEFAULT_ERROR.status)
    }
}
