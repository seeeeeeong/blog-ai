# blog-ai

Spring Boot service for crawling, embedding, similarity search, and RAG chat workflows.

## Repository Structure

Feature-first with **DDD-style layers** (`api / application / domain / infrastructure`) inside each feature. **One type per file.**

```text
src/main/kotlin/com/blog/ai
├── BlogAiApplication.kt
├── global/         # cross-cutting infra: admin, config, error, persistence, properties, response, text
├── article/        # application/, domain/, infrastructure/  (no HTTP surface)
├── blog/           # application/, domain/, infrastructure/
├── chat/           # api/, application/{session,memory,ratelimit,retrieval}/, domain/, infrastructure/{session,memory,ratelimit,rerank}/
├── crawl/          # application/, domain/, infrastructure/parser/
├── post/           # api/{sync,similar}/, application/{sync,embedding,similar}/, domain/, infrastructure/
├── rag/            # application/{embedding}/, domain/, infrastructure/  (RagChunkRepository internal)
└── job/            # *Job.kt — thin @Scheduled orchestrators
```

See [docs/conventions/clean-code.md](docs/conventions/clean-code.md) for the full convention.

## Quality Gates

- `./gradlew check` (runs tests + ktlintCheck + detekt + ArchitectureBoundaryTest)
- `./gradlew test`
- `./gradlew ktlintCheck`
- `./gradlew detekt`
