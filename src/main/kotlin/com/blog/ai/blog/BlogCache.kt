package com.blog.ai.blog

import com.blog.ai.blog.BlogRepository
import com.blog.ai.blog.toBlog
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class BlogCache(
    private val blogRepository: BlogRepository,
) {
    @Cacheable("blogs")
    fun getActiveBlogs(): List<Blog> = blogRepository.findAllByActiveTrue().map { it.toBlog() }

    @CacheEvict("blogs", allEntries = true)
    fun evictAll() {
    }
}
