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

    override fun equals(other: Any?): Boolean { ... Hibernate.getClass ... }
    override fun hashCode(): Int = Hibernate.getClass(this).hashCode()
}
```

- Use `Hibernate.getClass(this)` for proxy-safe `equals`/`hashCode`
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
