package com.blog.ai.core.api.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
@EnableCaching
class CacheConfig {

    companion object {
        private const val BLOG_CACHE_MAX_SIZE = 100L
        private val BLOG_CACHE_TTL: Duration = Duration.ofHours(6)
        private const val SIMILAR_CACHE_MAX_SIZE = 200L
        private val SIMILAR_CACHE_TTL: Duration = Duration.ofHours(1)
    }

    @Bean
    fun cacheManager(): CacheManager {
        val manager = CaffeineCacheManager()

        manager.registerCustomCache(
            "blogs",
            Caffeine.newBuilder()
                .expireAfterWrite(BLOG_CACHE_TTL)
                .maximumSize(BLOG_CACHE_MAX_SIZE)
                .build(),
        )
        manager.registerCustomCache(
            "similar",
            Caffeine.newBuilder()
                .expireAfterWrite(SIMILAR_CACHE_TTL)
                .maximumSize(SIMILAR_CACHE_MAX_SIZE)
                .build(),
        )

        return manager
    }
}
