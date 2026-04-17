package com.blog.ai.storage.chat

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ChatSessionRepository : JpaRepository<ChatSessionEntity, UUID>
