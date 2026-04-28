package com.blog.ai.global.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CachingConfigurer
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
@EnableCaching
class CacheConfig : CachingConfigurer {
    @Bean
    override fun cacheManager(): CacheManager =
        CaffeineCacheManager().apply {
            registerCustomCache(
                "blogs",
                Caffeine
                    .newBuilder()
                    .expireAfterWrite(Duration.ofHours(6))
                    .maximumSize(100)
                    .build(),
            )
        }
}
