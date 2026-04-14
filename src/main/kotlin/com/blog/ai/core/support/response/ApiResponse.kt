package com.blog.ai.core.support.response

import com.blog.ai.core.support.error.ErrorMessage
import com.blog.ai.core.support.error.ErrorType

data class ApiResponse<T>(
    val result: ResultType,
    val data: T? = null,
    val error: ErrorMessage? = null,
) {
    companion object {
        fun success(): ApiResponse<Any> = ApiResponse(result = ResultType.SUCCESS)

        fun <T> success(data: T): ApiResponse<T> = ApiResponse(result = ResultType.SUCCESS, data = data)

        fun error(errorType: ErrorType, message: String? = null): ApiResponse<Any> = ApiResponse(
            result = ResultType.ERROR,
            error = ErrorMessage(
                code = errorType.code,
                message = message ?: errorType.message,
            ),
        )
    }
}
