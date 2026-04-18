package com.blog.ai.core.support.response

import com.blog.ai.core.support.error.ErrorMessage
import com.blog.ai.core.support.error.ErrorType

data class ApiResponse<T>(
    val result: ResultType,
    val data: T? = null,
    val error: ErrorMessage? = null,
) {
    companion object {
        fun success(): ApiResponse<Any> = ApiResponse(ResultType.SUCCESS, null, null)

        fun <S> success(data: S): ApiResponse<S> = ApiResponse(ResultType.SUCCESS, data, null)

        fun <S> error(
            errorType: ErrorType,
            data: Any? = null,
            message: String? = null,
        ): ApiResponse<S> = ApiResponse(ResultType.ERROR, null, ErrorMessage(errorType, data, message))
    }
}
