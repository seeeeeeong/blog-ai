package com.blog.ai.core.support.error

class CoreException(
    val errorType: ErrorType,
    val data: Map<String, Any?>? = null,
    override val message: String = errorType.message,
    override val cause: Throwable? = null,
) : RuntimeException(message, cause) {
    constructor(errorType: ErrorType, cause: Throwable) : this(
        errorType = errorType,
        data = null,
        message = errorType.message,
        cause = cause,
    )

    constructor(errorType: ErrorType, message: String) : this(
        errorType = errorType,
        data = null,
        message = message,
        cause = null,
    )
}
