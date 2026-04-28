# blog-ai

Spring Boot service for crawling, embedding, similarity search, and RAG chat workflows.

## Repository Structure

This repository follows the same skeleton as `blog-api`. The current code is mid-migration: PR0 (this commit) updates the convention docs only, PR1 performs the package move + rename, PR2 unifies the embedding pipelines.

Target structure (post-PR1):

```text
src/main/kotlin/com/blog/ai
├── BlogAiApplication.kt
├── global
│   ├── config
│   ├── error
│   ├── response
│   ├── properties
│   ├── text
│   └── jdbc
├── article
├── blog
├── crawl
├── chat
├── post
├── rag
└── scheduler
```

Each feature package owns its `XxxService.kt` / `XxxApi.kt` / `XxxStore.kt` / optional `XxxClient.kt` / `XxxPreflight.kt`. Schedulers live in `scheduler/XxxJob.kt`.

Use [docs/conventions/clean-code.md](/Users/sinseonglee/Desktop/blog-ai/docs/conventions/clean-code.md) as the repository-wide refactoring baseline.

## Quality Gates

- `./gradlew test`
- `./gradlew ktlintCheck`
- `./gradlew detekt`
