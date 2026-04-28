package com.blog.ai.global.error

class AppException(
    val errorType: ErrorCode,
    val data: Map<String, Any?>? = null,
    override val message: String = errorType.message,
    override val cause: Throwable? = null,
) : RuntimeException(message, cause) {
    constructor(errorType: ErrorCode, cause: Throwable) : this(
        errorType = errorType,
        data = null,
        message = errorType.message,
        cause = cause,
    )

    constructor(errorType: ErrorCode, message: String) : this(
        errorType = errorType,
        data = null,
        message = message,
        cause = null,
    )
}
