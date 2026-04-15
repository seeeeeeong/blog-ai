# blog-ai

Spring Boot service for crawling, embedding, similarity search, and RAG chat workflows.

## Repository Structure

This repository follows the same skeleton as `blog-api`.

```text
com.blog.ai
├── core
│   ├── api
│   │   ├── config
│   │   └── controller/v1
│   │       ├── request
│   │       └── response
│   ├── domain
│   └── support
│       ├── error
│       ├── properties
│       └── response
├── scheduler
└── storage
```

Use [docs/conventions/clean-code.md](/Users/sinseonglee/Desktop/blog-ai/docs/conventions/clean-code.md) as the repository-wide refactoring baseline.

## Quality Gates

- `./gradlew test`
- `./gradlew ktlintCheck`
- `./gradlew detekt`

