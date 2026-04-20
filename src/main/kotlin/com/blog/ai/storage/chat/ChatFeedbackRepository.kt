package com.blog.ai.storage.chat

import org.springframework.data.jpa.repository.JpaRepository

interface ChatFeedbackRepository : JpaRepository<ChatFeedbackEntity, Long>
