package com.blog.ai.chat.infrastructure.session

import com.blog.ai.chat.infrastructure.session.ChatSessionEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ChatSessionRepository : JpaRepository<ChatSessionEntity, UUID>
