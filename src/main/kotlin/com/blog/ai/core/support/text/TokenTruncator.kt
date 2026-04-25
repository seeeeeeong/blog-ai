package com.blog.ai.core.support.text

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.EncodingType

object TokenTruncator {
    private val encoding = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE)

    fun truncate(
        text: String,
        maxTokens: Int,
    ): String {
        require(maxTokens > 0) { "maxTokens must be positive: $maxTokens" }
        val result = encoding.encode(text, maxTokens)
        if (result.isTruncated) return encoding.decode(result.tokens)
        return text
    }

    fun countTokens(text: String): Int = encoding.countTokens(text)
}
