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

    @Bean
    fun cacheManager(): CacheManager {
        val manager = CaffeineCacheManager()

        manager.registerCustomCache(
            "blogs",
            Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(6))
                .maximumSize(100)
                .build(),
        )
        manager.registerCustomCache(
            "similar",
            Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(200)
                .build(),
        )

        return manager
    }
}
