package com.blog.ai.global.response

import com.blog.ai.global.error.ErrorMessage
import com.blog.ai.global.error.ErrorCode

data class ApiResponse<T>(
    val result: ResultStatus,
    val data: T? = null,
    val error: ErrorMessage? = null,
) {
    companion object {
        fun success(): ApiResponse<Any> = ApiResponse(ResultStatus.SUCCESS, null, null)

        fun <S> success(data: S): ApiResponse<S> = ApiResponse(ResultStatus.SUCCESS, data, null)

        fun <S> error(
            errorType: ErrorCode,
            data: Any? = null,
            message: String? = null,
        ): ApiResponse<S> = ApiResponse(ResultStatus.ERROR, null, ErrorMessage(errorType, data, message))
    }
}
