import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("plugin.jpa") version "2.3.21"
    id("org.springframework.boot") version "3.5.9"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("dev.detekt") version "2.0.0-alpha.3"
    id("org.jooq.jooq-codegen-gradle") version "3.19.29"
}

group = "com.blog"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

extra["springAiVersion"] = "1.0.0"

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    sourceSets {
        main {
            kotlin.srcDir("src/generated/kotlin")
        }
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // Sentry
    implementation("io.sentry:sentry-spring-boot-starter-jakarta:7.14.0")
    implementation("io.sentry:sentry-logback:7.14.0")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Spring AI
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("org.springframework.ai:spring-ai-rag")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    jooqCodegen("org.postgresql:postgresql")

    // Cache
    implementation("com.github.ben-manes.caffeine:caffeine")

    // RSS + HTML
    implementation("com.rometools:rome:2.1.0")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("net.dankito.readability4j:readability4j:1.0.8")

    // Tokenizer (OpenAI cl100k_base) for token-aware truncation
    implementation("com.knuddels:jtokkit:1.1.0")

    // ShedLock — distributed scheduler coordination (2 instances)
    implementation("net.javacrumbs.shedlock:shedlock-spring:7.7.0")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.7.0")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

jooq {
    configuration {
        jdbc {
            driver = "org.postgresql.Driver"
            url = "jdbc:postgresql://${System.getenv("DB_HOST") ?: "localhost"}:5432/blog_ai"
            user = System.getenv("DB_USERNAME") ?: "postgres"
            password = System.getenv("DB_PASSWORD") ?: "postgres"
        }
        generator {
            name = "org.jooq.codegen.KotlinGenerator"
            database {
                name = "org.jooq.meta.postgres.PostgresDatabase"
                inputSchema = "public"
                includes = "rag_chunks"
            }
            generate {
                isDeprecated = false
                isDeprecationOnUnknownTypes = false
                isRecords = true
                isPojos = false
                isDaos = false
                isFluentSetters = true
            }
            target {
                packageName = "com.blog.ai.jooq"
                directory = "$rootDir/src/generated/kotlin"
            }
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    mustRunAfter("jooqCodegen")
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

ktlint {
    version.set("1.8.0")
    ignoreFailures.set(false)
    filter {
        exclude { element -> element.file.path.contains("/src/generated/") }
    }
}

tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
    mustRunAfter("jooqCodegen")
    exclude { element -> element.file.path.contains("/src/generated/") }
}

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/detekt-baseline.xml")
    buildUponDefaultConfig = true
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    mustRunAfter("jooqCodegen")
    exclude { element -> element.file.path.contains("/src/generated/") }
}

tasks.named<BootJar>("bootJar") {
    archiveBaseName.set("blog-ai")
}

tasks.named<Jar>("jar") {
    enabled = false
}

tasks.named("check") {
    dependsOn("ktlintCheck", "detekt")
}
