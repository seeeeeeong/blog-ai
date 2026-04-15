package com.blog.ai.core.api.controller

import com.blog.ai.core.support.error.CoreException
import com.blog.ai.core.support.error.ErrorType
import com.blog.ai.core.support.response.ApiResponse
import jakarta.validation.ConstraintViolationException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.logging.LogLevel
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

private val logger = KotlinLogging.logger {}

@RestControllerAdvice
class ApiControllerAdvice {

    @ExceptionHandler(CoreException::class)
    fun handleCoreException(e: CoreException): ResponseEntity<ApiResponse<Any>> {
        when (e.errorType.logLevel) {
            LogLevel.ERROR -> logger.error(e) { "CoreException : ${e.message}" }
            LogLevel.WARN -> logger.warn(e) { "CoreException : ${e.message}" }
            else -> logger.info(e) { "CoreException : ${e.message}" }
        }
        return ResponseEntity(ApiResponse.error(e.errorType, e.data, e.message), e.errorType.status)
    }

    @ExceptionHandler(
        ConstraintViolationException::class,
        MethodArgumentTypeMismatchException::class,
        MethodArgumentNotValidException::class,
        MissingServletRequestParameterException::class,
        HttpMessageNotReadableException::class,
    )
    fun handleBadRequestException(e: Exception): ResponseEntity<ApiResponse<Any>> {
        logger.info { "Bad request : ${e.message}" }
        return ResponseEntity(ApiResponse.error(ErrorType.INVALID_INPUT), ErrorType.INVALID_INPUT.status)
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ApiResponse<Any>> {
        logger.error(e) { "Exception : ${e.message}" }
        return ResponseEntity(ApiResponse.error(ErrorType.DEFAULT_ERROR), ErrorType.DEFAULT_ERROR.status)
    }
}
