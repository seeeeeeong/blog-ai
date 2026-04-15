# blog-ai Working Guide

## Repository Context

- Crawling, embedding, similarity search, and RAG chat
- Kotlin + Spring Boot + Spring AI + JPA + PostgreSQL/pgvector
- Common package roots: `core`, `scheduler`, `storage`

## Non-Negotiable Structure

- HTTP entry points live in `core/api/controller/v1`
- Application services live in `core/domain`
- Shared support code lives in `core/support`
- Scheduled jobs live in `scheduler` or the owning domain package when the scheduler is tightly coupled
- JPA entities and repositories live in `storage`

Do not add new top-level source packages when an existing branch already owns the concern.

## Clean Code Standard

Follow [docs/conventions/clean-code.md](/Users/sinseonglee/Desktop/blog-ai/docs/conventions/clean-code.md).

The short version:

- prefer obvious names over compact names,
- keep controller, service, and storage responsibilities separate,
- preserve existing user edits and avoid broad rewrites,
- make incremental structural improvements before deeper refactors.

## Verification

Before closing work, run the smallest useful Gradle verification set for the touched area, plus formatting or static analysis when configuration changes are involved.
