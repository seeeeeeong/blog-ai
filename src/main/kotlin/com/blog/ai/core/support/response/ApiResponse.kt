package com.blog.ai.core.support.response

import com.blog.ai.core.support.error.ErrorMessage
import com.blog.ai.core.support.error.ErrorType

data class ApiResponse<T>(
    val result: ResultType,
    val data: T? = null,
    val error: ErrorMessage? = null,
) {
    companion object {
        fun success(): ApiResponse<Any> {
            return ApiResponse(ResultType.SUCCESS, null, null)
        }

        fun <S> success(data: S): ApiResponse<S> {
            return ApiResponse(ResultType.SUCCESS, data, null)
        }

        fun <S> error(errorType: ErrorType, data: Any? = null): ApiResponse<S> {
            return ApiResponse(ResultType.ERROR, null, ErrorMessage(errorType, data))
        }
    }
}
