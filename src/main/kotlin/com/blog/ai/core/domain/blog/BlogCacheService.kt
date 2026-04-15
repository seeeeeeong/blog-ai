package com.blog.ai.core.domain.blog

import com.blog.ai.storage.blog.BlogRepository
import com.blog.ai.storage.blog.toBlog
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class BlogCacheService(
    private val blogRepository: BlogRepository,
) {

    @Cacheable("blogs")
    fun getActiveBlogs(): List<Blog> {
        return blogRepository.findAllByActiveTrue().map { it.toBlog() }
    }

    @CacheEvict("blogs", allEntries = true)
    fun evictAll() {
    }
}
