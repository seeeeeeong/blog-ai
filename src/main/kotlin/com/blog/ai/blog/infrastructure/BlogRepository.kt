package com.blog.ai.blog.infrastructure

import com.blog.ai.blog.infrastructure.BlogEntity
import org.springframework.data.jpa.repository.JpaRepository

interface BlogRepository : JpaRepository<BlogEntity, Long> {
    fun findAllByActiveTrue(): List<BlogEntity>
}
