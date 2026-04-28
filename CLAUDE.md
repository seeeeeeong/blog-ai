# blog-ai

RAG-enabled blog search platform built with Kotlin + Spring Boot 3 + Spring AI + PostgreSQL/pgVector.
Crawls Korean/international tech blogs, embeds content, and provides similarity search and AI chat.

## Tech Stack

- Kotlin 2.3, Spring Boot 3.5, Spring AI 1.0 (OpenAI)
- PostgreSQL + pgVector + Flyway migrations
- Caffeine cache, Rome RSS parser, Jsoup
- Gradle (ktlint, detekt)

---

## Package Structure (Non-Negotiable)

> **Status:** This section reflects the **target** structure after PR1 lands. Current code still uses the legacy shape (`core/api`, `core/domain`, `core/support`, `storage/`). PR0 (this document update) only fixes conventions; PR1 performs the move + rename in one mechanical pass; PR2 unifies the embed pipelines.

Target tree:

```
com.blog.ai
├── BlogAiApplication.kt
├── global                  # cross-cutting infra (formerly core/api/config + core/support)
│   ├── config              # AppConfig, AiConfig, CacheConfig, SchedulerLockConfig, WebConfig
│   ├── error               # AppException, ErrorCode, ErrorMessage
│   ├── response            # ApiResponse<T>, PageResponse, ResultStatus
│   ├── properties          # AppProperties
│   ├── text                # TextSplitter, TokenTruncator, EmbeddingBatcher
│   └── jdbc                # JdbcTimeMapper
│
├── article                 # RSS-crawled articles + embedding orchestration + admin
├── blog                    # Blog source registry + cache
├── crawl                   # RSS/HTML ingestion (parser, scraper, cleaner)
├── chat                    # Chat session + RAG retrieval (retriever, planner, expander, rerank)
├── post                    # External blog-post sync + similarity
├── rag                     # Shared rag_chunks store + ChunkEnricher (source-agnostic)
└── scheduler               # XxxJob.kt — thin @Scheduled orchestrators
```

Each feature package owns 2–5 files using these suffixes:

| File | Holds |
|---|---|
| `{Feature}Service.kt` | use-case service + private data classes (Snapshot, Batch, scoped Command) |
| `{Feature}Api.kt` | `@RestController`(s) for the feature + their Request/Response DTOs |
| `{Feature}Store.kt` | `@Entity` + `Repository` + persistence Commands + entity extension functions |
| `{Feature}Client.kt` | external HTTP/SDK client + its response DTOs |
| `{Feature}Preflight.kt` | DB read/write that must run *before* an external call (LLM/rerank/scrape) so the txn does not span the network round-trip |
| `{Feature}Committer.kt` | DB write that must run *after* an external call, kept as a separate Spring bean so the caller's `@Transactional` does not span the round-trip. Use only for this boundary preservation — do not use `Committer` as a generic helper suffix. |

`scheduler/{Feature}Job.kt` for `@Scheduled` orchestrators (one top-level package, separate from feature packages).

**Never do:**
- Reintroduce a top-level `core/` or `storage/` package
- Access another feature's `XxxStore` from outside that feature (e.g., `chat` controllers may not import `post.PostStore`)
- Access any `XxxStore` from a controller or scheduler — always go through a domain service
- Cross-feature imports for anything except `global/*` and `rag/*` shared types
- Expose JPA entities in controller responses or domain service parameters
- Pass Entity objects between domain services (use domain models or IDs)

---

## Coding Conventions

### Naming

| Target | Rule | Example |
|--------|------|---------|
| Command object | `{Entity}{Action}Command` | `SaveChunkCommand` |
| Service method | verb + object | `crawlAll`, `embedPending`, `findSimilar` |
| Request DTO | `{Entity}{Action}Request` | `SimilarRequest` |
| Response DTO | `{Entity}Response` | `SimilarResponse`, `TrendingResponse` |
| Repository query | Spring Data naming or `@Query` | `findUnembedded`, `existsByUrlHash` |
| Validation private method | `require{Condition}` | `requireAdminKey` |
| Factory method | `create()` | `ArticleEntity.create(...)` |

### Style Rules

- Always use trailing commas
- Never use `!!` — use `?:`, `?.let`, or `requireNotNull` instead
- Extract a Command object when function parameters exceed 4
- Never branch on Boolean parameters — split into separate functions
- Keep branching flat with guard clauses (max 2 levels of nesting)
- Functions must stay under 40 lines
- Log in English using KotlinLogging
- Max line length: 120 characters
- File grouping follows **reason to change**, not type. A feature's full flow lives in 2–5 files inside one package — `XxxService.kt`, `XxxApi.kt`, `XxxStore.kt`, optional `XxxClient.kt` / `XxxPreflight.kt`. `XxxService.kt` may co-locate its private data classes (Snapshot/Batch/scoped Command). `XxxStore.kt` may co-locate `@Entity` / `Repository` / persistence Command / entity extension. Promote a type to its own file only when a second feature imports it. Domain models (`Article`, `Blog`, `Post`) and cross-package contract types (`RagChunkHit`, `RagSourceType`) own their file.

```kotlin
// Bad — fan-out across 6 files for one feature flow
// PostEmbedService.kt, PostEmbedCommitter.kt, PostEmbedWorker.kt,
// PostEmbedInternals.kt, PostEmbedSnapshot.kt, SavePostChunkCommand.kt

// Good — one feature in 2–4 files
// PostApi.kt           → controllers + DTOs
// PostEmbedService.kt  → service + private Snapshot/Batch/Command data classes
// PostStore.kt         → BlogPostEntity + BlogPostRepository + extensions
// scheduler/PostJob.kt → @Scheduled trigger
```

  If `XxxService.kt` grows past ~400 lines, split by **use case** (`XxxAdminService.kt`, `XxxSyncService.kt`), not by extracting a `Committer`/`Worker` helper. Use `internal` visibility on co-located data classes when the consumer is private to the file; drop it whenever Kotlin's `EXPOSED_PARAMETER_TYPE`, Jackson, or test access requires public.
- Keep `if` conditions plain. Avoid `!`, avoid safe-call chains (`x?.foo() == true`), avoid null comparisons buried in compound conditions. Flatten the value first — with `?: return`, `?: continue`, `takeIf { ... }`, or a named boolean — so the `if` itself reads as a domain concept on a non-nullable receiver. Prefer a positive condition with early return, then handle the failure case after; the happy path reads forward instead of as "not the bad thing." Prefer the positive form of negated extension calls (`isNotBlank()` over `!isNullOrBlank()`, `isNotEmpty()` over `!isEmpty()`, `result.isTruncated` over `!result.isTruncated` with branches swapped).

```kotlin
// Bad — negation buried in a method call
if (!chatSessionRepository.existsById(sessionId)) {
    throw CoreException(ErrorType.SESSION_NOT_FOUND)
}

// Good — name the boolean, branch positively, throw after
val sessionExists = chatSessionRepository.existsById(sessionId)
if (sessionExists) return
throw CoreException(ErrorType.SESSION_NOT_FOUND)

// Bad — negated extension call, or safe-call chain in the condition
if (!cleaned.isNullOrBlank()) return cleaned
if (cleaned?.isNotBlank() == true) return cleaned

// Good — flatten with `?: continue`/`?: return`, then a plain positive check
val cleaned = contentCleaner.clean(element.html()) ?: continue
if (cleaned.isNotBlank()) return cleaned
```

- Use `?.` (safe-call) sparingly — only when each step in the chain has a genuinely nullable receiver from an external boundary you don't control. Don't paper over branching logic with long `?.foo()?.bar()?.baz()` chains; flatten to non-null at the earliest point with `?: return` / `?: continue`, then operate on the non-nullable value. Multiple `?.` calls in a row are a code smell — usually one of the receivers is non-nullable in practice and the chain is hiding a clearer guard. Two `?.` calls walking a third-party object graph (e.g. `entry.contents?.firstOrNull()?.value`) are fine — that's the "necessary moment."

```kotlin
// Bad — long safe-call chain hiding the control flow
val firstIp = request.getHeader("X-Forwarded-For")
    ?.split(",")
    ?.firstOrNull()
    ?.trim()
    ?.takeIf { it.isNotBlank() }
return firstIp ?: request.remoteAddr

// Good — bail out early via elvis, then work on a non-null String
val forwarded = request.getHeader("X-Forwarded-For") ?: return request.remoteAddr
val firstIp = forwarded.split(",").first().trim()
if (firstIp.isNotBlank()) return firstIp
return request.remoteAddr
```

### API Response

All responses are wrapped in `ApiResponse<T>`:

```kotlin
// Success
ApiResponse.success(data)
ApiResponse.success() // for no-body responses

// Errors — throw CoreException, ApiControllerAdvice handles conversion
throw CoreException(ErrorType.ARTICLE_NOT_FOUND)
```

### Request DTO Pattern

```kotlin
data class SimilarRequest(
    @field:NotBlank val vector: String,
)
```

- Validation annotations require `@field:` prefix
- Controllers must use `@Valid @RequestBody`

### Response DTO Pattern

```kotlin
data class SimilarResponse(
    val id: Long,
    val title: String,
    ...
) {
    companion object {
        fun of(article: SimilarArticle) = SimilarResponse(...)
    }
}
```

- Use `companion object { fun of() }` factory method
- Map from domain objects only — never reference entities directly

---

## Entity & Storage Rules

### Entity

```kotlin
@Entity
@Table(name = "articles")
class ArticleEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val title: String,
    embedError: String? = null,
) {
    @Column(name = "embed_error", columnDefinition = "TEXT")
    var embedError: String? = embedError
        protected set

    fun markEmbedError(error: String) { ... }

    companion object {
        fun create(...): ArticleEntity { ... }
    }
}
```

- Mutable fields: constructor param → body `var ... protected set`
- Encapsulate state changes behind methods
- Use `companion object { fun create() }` factory with `require` validation
- `id: Long? = null` for auto-generated IDs

### Extension Functions

Entity → Domain conversion lives in `{Entity}Extensions.kt`:

```kotlin
fun ArticleEntity.toArticle() = Article(
    id = requireNotNull(id) { "ArticleEntity.id must not be null after persistence" },
    ...
)
```

---

## Database Migration (Flyway)

- Filename: `V{N}__{snake_case_description}.sql`
- Currently at V3 — next migration starts at V4
- Use `IF EXISTS` / `IF NOT EXISTS` for idempotency
- Use `CREATE EXTENSION IF NOT EXISTS` for pgVector

---

## Error Handling

```kotlin
// Define: ErrorType enum
ARTICLE_NOT_FOUND(HttpStatus.NOT_FOUND, "ARTICLE_001", "Article not found", LogLevel.WARN)

// Throw: CoreException
throw CoreException(ErrorType.ARTICLE_NOT_FOUND)

// Catch: ApiControllerAdvice auto-converts to ApiResponse.error()
```

- Add new errors to the `ErrorType` enum
- Code format: `{DOMAIN}_{number}` (AUTH_001, ARTICLE_001, EMBED_001, CRAWL_001, COMMON_001)
- logLevel: client fault (4xx) → INFO/WARN, server fault (5xx) → ERROR

---

## Scheduler Rules

Schedulers are thin orchestrators. They:
- Call domain services to perform work
- Catch and log exceptions
- Never access repositories directly

```kotlin
@Scheduled(cron = "0 0 */6 * * *")
fun fetch() {
    try {
        hnTrendingService.fetchAndSave()
    } catch (e: Exception) {
        log.error(e) { "HN trending update failed" }
    }
}
```

---

## Verification Checklist

After any code change:

1. `./gradlew check` — tests + ktlintCheck + detekt pass
2. New feature or bugfix → add tests
3. Schema change → add Flyway migration
4. New error type → add to `ErrorType` enum
5. New admin endpoint → validate `X-Admin-Key` header

---

## Rename Migration (Planned for PR1)

PR0 leaves all code untouched. PR1 performs these renames mechanically; PR2 unifies the embedding pipelines.

| Legacy | Canonical (PR1+) |
|---|---|
| `CoreException` | `AppException` |
| `ErrorType` | `ErrorCode` |
| `ResultType` | `ResultStatus` |
| `JdbcTimestamps` | `JdbcTimeMapper` |
| `ChatClientConfig` | `AiConfig` |
| `ShedLockConfig` | `SchedulerLockConfig` |
| `*Scheduler.kt` (5 files) | `*Job.kt` |
| `BlogPost*` inside `post/` | `Post*` (package context) |
| Separate `*Repository.kt` + `*Entity.kt` | `XxxStore.kt` (consolidated) |
| `controller/v1/{request,response}/*` | `{feature}/XxxApi.kt` (co-located with controller) |

Until PR1 ships, code references in this document and in error/log strings still use the legacy names (`CoreException`, `ErrorType`, etc.). The convention rules above describe the post-PR1 shape.

---

## What NOT To Do

- Reintroduce a top-level `core/` or `storage/` package
- Access another feature's `XxxStore` from outside that feature
- Return entities directly as responses
- Call repositories from controllers or schedulers
- Pass Entity objects as domain service parameters
- Write operations without `@Transactional`
- Log API keys, tokens, or secrets
- Use `@Async` on self-calls (extract to separate @Service)
- Swallow errors with `runCatching { }.getOrDefault()`
- Declare work complete without running `./gradlew check`
- Mix a structure-move PR with a behavior-change PR
- Introduce new patterns without discussion
