package com.blog.ai.chat.domain

enum class ChatMode {
    AUTHOR_POST,
    EXTERNAL_ARTICLE,
    ;

    companion object {
        val DEFAULT: ChatMode = EXTERNAL_ARTICLE
    }
}
