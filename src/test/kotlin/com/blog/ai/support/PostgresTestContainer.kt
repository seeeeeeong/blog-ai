package com.blog.ai.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class PostgresTestContainer {
    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer<*> =
        PostgreSQLContainer(
            DockerImageName
                .parse("pgvector/pgvector:pg16")
                .asCompatibleSubstituteFor("postgres"),
        ).withDatabaseName("blog_ai_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true)
}
