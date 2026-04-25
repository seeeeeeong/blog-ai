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

```
com.blog.ai
├── core
│   ├── api
│   │   ├── config          # AppConfig, CacheConfig, ChatClientConfig, WebConfig
│   │   └── controller/v1
│   │       ├── request      # Request DTOs (data class, validation)
│   │       └── response     # Response DTOs (companion of())
│   ├── domain
│   │   └── {context}        # Service, Command, Domain model
│   │       ├── article      # ArticleAdminService, ArticleChunkService, ArticleEmbedService
│   │       ├── blog         # BlogCacheService
│   │       ├── chat         # ChatService
│   │       ├── crawl        # CrawlService, CrawlAsyncService, ArticleSaveService, RssFeedParser
│   │       └── similar      # SimilarService
│   └── support
│       ├── error            # CoreException, ErrorType, ErrorMessage
│       ├── properties       # @ConfigurationProperties
│       └── response         # ApiResponse<T>, PageResponse, ResultType
├── scheduler                # CrawlScheduler, EmbeddingRetryScheduler
└── storage
    └── {context}            # Entity, Repository, Extensions
```

**Never do:**
- Add top-level packages outside `core`, `scheduler`, and `storage`
- Access `storage` directly from `controller` (always go through `domain` services)
- Access `storage` directly from `scheduler` (always go through `domain` services)
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
- Split files by *public coupling*, not by "one type per file." A type lives in its own file when it is part of a **public contract** — Request/Response DTOs (Controller↔Service boundary), domain models, types accepted from or returned to another bounded context, Commands consumed by multiple services or schedulers, Hits/Results visible across layers. **Internal implementation data types** of a single feature flow — Snapshots, Batches, intermediate aggregates, Commands consumed only by helpers of one service — go in a sibling **`{Feature}Internals.kt`** file next to the owner service in the same package. **Helper services / Spring components** (Committer, Worker, etc.) always live in their own `.kt` file even when they're scoped to one flow — never colocate `@Service` / `@Component` classes with data classes in `Internals.kt`, and never put data classes inside the same file as a service class. The aim is "follow one feature in two or three files (use-case service + Internals.kt + helper service if any)," not "one type per file regardless." If a type acquires a caller outside its origin feature, promote it to its own file.

```kotlin
// Bad — every internal step in its own file, fan-out across 5 files for one flow
// ArticleEmbedService.kt, ArticleEmbedCommitter.kt, ArticleEmbedSnapshot.kt,
// ArticleEmbedBatch.kt, ArticleEmbedCommitCommand.kt

// Bad — service mixed with its data classes in the same file
// ArticleEmbedInternals.kt
data class ArticleEmbedCommitCommand(...)
@Service
class ArticleEmbedCommitter(...) { ... }   // service does not belong in Internals.kt

// Good — service files hold only services; Internals.kt holds only data classes
// ArticleEmbedService.kt          → use-case service only
class ArticleEmbedService(
    private val articleEmbedCommitter: ArticleEmbedCommitter,
    ...
) { ... }

// ArticleEmbedCommitter.kt        → helper service only
@Service
class ArticleEmbedCommitter(...) { ... }

// ArticleEmbedInternals.kt        → sibling file, data classes only
internal data class ArticleEmbedSnapshot(...)
internal data class ArticleEmbedBatch(...)
data class ArticleEmbedCommitCommand(...)
data class SaveChunkCommand(...)

// Public Request/Response/domain model — still own file
// ArticleResponse.kt, Article.kt, SimilarRequest.kt
```

  Prefer `internal` visibility for co-located data types — it documents the single-owner intent and stops cross-package callers from forming. Treat it as a recommendation, not a hard rule: Spring DI (a `public` service cannot accept an `internal` constructor parameter — Kotlin's `EXPOSED_PARAMETER_TYPE`), public method signatures that include the type, Jackson serialization, or test access may legitimately require broader visibility. Drop `internal` when one of those constraints actually bites; don't add it just to satisfy a checklist.
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

## What NOT To Do

- Return entities directly as responses
- Call repositories from controllers or schedulers
- Pass Entity objects as domain service parameters
- Write operations without `@Transactional`
- Log API keys, tokens, or secrets
- Use `@Async` on self-calls (extract to separate @Service)
- Swallow errors with `runCatching { }.getOrDefault()`
- Declare work complete without running `./gradlew check`
- Introduce new patterns without discussion
