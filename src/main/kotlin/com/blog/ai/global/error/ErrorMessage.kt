package com.blog.ai.global.error

data class ErrorMessage(
    val code: String,
    val message: String,
    val data: Any? = null,
) {
    constructor(errorType: ErrorCode, data: Any? = null, message: String? = null) : this(
        code = errorType.code,
        message = message ?: errorType.message,
        data = data,
    )
}
