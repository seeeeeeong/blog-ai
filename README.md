# blog-ai

Spring Boot service for crawling, embedding, similarity search, and RAG chat workflows.

## Repository Structure

Feature-first. **One type per file.** Each feature picks the shape that fits its domain — vertical slice (use-case sub-packages), functional cohesion (sub-packages by concern), or flat — instead of forcing a uniform layered template.

```text
src/main/kotlin/com/blog/ai
├── BlogAiApplication.kt
├── global/         # cross-cutting infra: admin, config, error, jdbc, jpa, properties, response, text
├── article/        # ArticleEntity + Repository + AdminService at root, embedding/ sub-package
├── blog/           # flat — Blog, BlogEntity, BlogRepository, BlogMapper, BlogCacheService
├── chat/           # ChatController + DTOs + ChatService at root; session/ memory/ rag/ ratelimit/ sub-packages
├── crawl/          # services + CrawlConstants at root; parser/ sub-package
├── post/           # PostEntity + PostRepository at root; sync/ embedding/ similar/ sub-packages
├── rag/            # services + repository + types at root; embedding/ sub-package
└── scheduler/      # *Job.kt — thin @Scheduled orchestrators
```

See [docs/conventions/clean-code.md](docs/conventions/clean-code.md) for the full convention and decision table for picking a feature shape.

## Quality Gates

- `./gradlew test`
- `./gradlew ktlintCheck`
- `./gradlew detekt`
- `./gradlew check` (runs all plus `ArchitectureBoundaryTest`)
