package com.blog.ai.chat.repository

import com.blog.ai.chat.entity.ChatSessionEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ChatSessionRepository : JpaRepository<ChatSessionEntity, UUID>
