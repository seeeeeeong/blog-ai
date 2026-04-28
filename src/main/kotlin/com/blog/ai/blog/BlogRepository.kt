package com.blog.ai.blog

import com.blog.ai.blog.BlogEntity
import org.springframework.data.jpa.repository.JpaRepository

interface BlogRepository : JpaRepository<BlogEntity, Long> {
    fun findAllByActiveTrue(): List<BlogEntity>
}
