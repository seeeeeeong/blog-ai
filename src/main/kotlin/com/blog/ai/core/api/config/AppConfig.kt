package com.blog.ai.core.api.config

import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AppConfig {
    @Bean
    fun kotlinModule(): KotlinModule =
        KotlinModule
            .Builder()
            .enable(KotlinFeature.NullIsSameAsDefault)
            .build()
}
