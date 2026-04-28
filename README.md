# blog-ai

Spring Boot service for crawling, embedding, similarity search, and RAG chat workflows.

## Repository Structure

Feature-first with layered sub-packages. Each feature owns its own `controller/` / `service/` / `entity/` / `repository/` / `model/` (and where applicable `mapper/`, `client/`, `parser/`, `support/`, `retriever/`, `memory/`, `request/`, `response/`). One type per file.

```text
src/main/kotlin/com/blog/ai
├── BlogAiApplication.kt
├── global
│   ├── admin       # operational REST endpoints
│   ├── config      # @Configuration beans
│   ├── error       # AppException, ErrorCode, ErrorMessage, ApiControllerAdvice
│   ├── jdbc        # JdbcTimeMapper
│   ├── jpa         # BaseTimeEntity (@MappedSuperclass)
│   ├── properties  # @ConfigurationProperties holders
│   ├── response    # ApiResponse, ResultStatus
│   └── text        # TextSplitter, TokenTruncator, EmbeddingBatcher
├── article         # service / entity / repository / model
├── blog            # service / entity / repository / model / mapper
├── chat            # controller / request / response / service / retriever / client / memory / entity / repository / model / mapper
├── crawl           # service / parser / client / model / support
├── post            # controller / request / response / service / entity / repository / model
├── rag             # service / embedding / repository / model
└── scheduler       # *Job.kt — thin @Scheduled orchestrators
```

Use [docs/conventions/clean-code.md](docs/conventions/clean-code.md) as the repository-wide refactoring baseline.

## Quality Gates

- `./gradlew test`
- `./gradlew ktlintCheck`
- `./gradlew detekt`
