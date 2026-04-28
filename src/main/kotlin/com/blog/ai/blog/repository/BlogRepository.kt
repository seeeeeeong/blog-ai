package com.blog.ai.blog.repository

import com.blog.ai.blog.entity.BlogEntity
import org.springframework.data.jpa.repository.JpaRepository

interface BlogRepository : JpaRepository<BlogEntity, Long> {
    fun findAllByActiveTrue(): List<BlogEntity>
}
