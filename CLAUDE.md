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

Feature-first with **DDD-style layers** inside each feature: `api / application / domain / infrastructure`. **One type per file.**

Apply layers only where the feature has enough files to justify them. Tiny features (e.g., `blog`) still use 4 sub-folders for consistency; mid-size features (`article`, `crawl`) skip `api` because they have no HTTP surface.

```
com.blog.ai
├── BlogAiApplication.kt
│
├── global                              # cross-cutting infra
│   ├── admin                           # operational REST endpoints (AdminController)
│   ├── config                          # @Configuration beans
│   ├── error                           # AppException, ErrorCode, ErrorMessage, ApiControllerAdvice
│   ├── persistence                     # BaseTimeEntity (JPA @MappedSuperclass), JdbcTimeMapper
│   ├── properties                      # @ConfigurationProperties holders
│   ├── response                        # ApiResponse<T>, ResultStatus
│   └── text                            # TextSplitter, TokenTruncator, EmbeddingBatcher
│
├── article                             # no HTTP surface
│   ├── application
│   │   ├── ArticleAdminService
│   │   └── embedding                   # ArticleEmbeddingService, ArticleEmbeddingWriter
│   ├── domain                          # ArticleEmbeddingResult, ArticleEmbeddingSnapshot
│   └── infrastructure                  # ArticleEntity, ArticleRepository
│
├── blog                                # no HTTP surface
│   ├── application                     # BlogCacheService
│   ├── domain                          # Blog
│   └── infrastructure                  # BlogEntity, BlogRepository, BlogMapper
│
├── crawl                               # no HTTP surface
│   ├── application                     # CrawlService, CrawlAsyncService, ArticleSaveService
│   ├── domain                          # CrawlConstants, ParsedArticle
│   └── infrastructure
│       └── parser                      # RssFeedParser, WebContentScraper, ContentCleaner
│
├── chat
│   ├── api                             # ChatController + ChatRequest + 2 responses
│   ├── application
│   │   ├── ChatService                 # main orchestrator
│   │   ├── memory                      # ChatMemoryStore (Spring AI ChatMemory bean)
│   │   ├── ratelimit                   # RateLimiter, ChatPreflight
│   │   ├── retrieval                   # ArticleRetriever, QueryPlanner, QueryExpander,
│   │   │                               #   ClarificationService, QueryEmbedding, RerankedExternalResult
│   │   └── session                     # ChatSessionService
│   ├── domain                          # ChatMessage, ChatAdvisorParams, RateLimitRequest, RateLimitOutcome
│   └── infrastructure
│       ├── memory                      # ChatMessageEntity, ChatMessageRepository, ChatMessageMapper
│       ├── ratelimit                   # RateLimitStore (JdbcTemplate)
│       ├── rerank                      # RerankClient (Jina API), JinaRerankResponse
│       └── session                     # ChatSessionEntity, ChatSessionRepository
│
├── post
│   ├── api
│   │   ├── similar                     # SimilarPostController + SimilarResponse + SimilarItem
│   │   └── sync                        # InternalPostController + SyncPostRequest + SyncPostResponse
│   ├── application
│   │   ├── embedding                   # PostEmbeddingService, PostEmbeddingWriter
│   │   ├── similar                     # SimilarPostService
│   │   └── sync                        # PostSyncService
│   ├── domain                          # SyncPost, SyncResult, EventType, PostEmbeddingSnapshot,
│   │                                   #   PostEmbeddingResult, SimilarArticle, SimilarResult, SimilarStatus
│   └── infrastructure                  # PostEntity, PostRepository
│
├── rag                                 # source-agnostic RAG pipeline (no HTTP surface)
│   ├── application
│   │   ├── RagSearchService            # CQRS-ish read side
│   │   ├── RagWriteService             # CQRS-ish write side
│   │   ├── ChunkEnricher
│   │   └── embedding                   # EmbeddingPipeline
│   ├── domain                          # RagSourceType, RagChunkGranularity, RagSearchQuery,
│   │                                   #   RagChunkWrite, RagChunkHit, ChunkEmbedding,
│   │                                   #   ChunkEmbeddingJob, DocumentEmbedding, EmbeddingDocument
│   └── infrastructure                  # RagChunkRepository (JdbcTemplate, rag-internal)
│
└── job                                 # *Job.kt — thin @Scheduled orchestrators
```

### Layer rules

| Layer | Holds |
|---|---|
| `api/` | `@RestController` + Request/Response DTOs (one type per file) |
| `application/` | `@Service` use-case classes — **no** top-level `data class` / `enum class` |
| `domain/` | Domain models, command inputs, results, statuses (one type per file) |
| `infrastructure/` | `@Entity`, `Repository`, mappers, external clients (HTTP/SDK) |

Sub-folders inside a layer (e.g., `application/embedding/`, `infrastructure/parser/`, `application/retrieval/`) split the layer further by concern — use them when a layer has 4+ tightly-related files.

### Service decomposition

External-API + DB write boundaries are preserved as **separate Spring beans**, never inlined:

| Suffix | Role | Lives in |
|---|---|---|
| `{Feature}Service` | use-case orchestrator | `application/` |
| `{Feature}Worker` | per-item processor (`@Async`/batch) | `application/` |
| `{Feature}Writer` | post-external-call DB write — own `@Transactional`, txn does not span the round-trip | `application/` |
| `{Feature}Preflight` | pre-external-call DB read/write — same boundary intent | `application/` |

`job/{Feature}Job.kt` for `@Scheduled` orchestrators (one job per file).

**Never do:**
- Reintroduce a top-level `core/`, `storage/`, or `scheduler/` package
- Reintroduce per-feature `controller/request/response/service/entity/repository/model/mapper` flat-template sub-packages (retired)
- Put a top-level `data class` or `enum class` inside an `application/` file (move to `domain/`)
- Cross-feature imports of another feature's `infrastructure/` (entities, repositories, external clients). Specifically: `RagChunkRepository` is **rag-internal** — outside callers must go through `RagSearchService` (read) or `RagWriteService` (write). Enforced by `ArchitectureBoundaryTest`.
- Access an entity/repository from a controller or job — always go through an application service
- Cross-feature imports for anything except `global/*` and `rag/domain/*` shared types
- Expose JPA entities in controller responses or service parameters
- Pass Entity objects between application services (use domain models or IDs)
- Inline a `Writer` / `Preflight` / `Worker` into its caller (would break the `@Transactional` / `@Async` proxy boundary)

---

## Coding Conventions

### Naming

| Target | Rule | Example |
|--------|------|---------|
| Service method | verb + object | `crawlAll`, `embedPending`, `findSimilar` |
| Request DTO | `{Entity}{Action}Request` | `SyncPostRequest`, `ChatRequest` |
| Response DTO | `{Entity}Response` | `SimilarResponse`, `SyncPostResponse` |
| Repository query | Spring Data naming or `@Query` | `findUnembedded`, `existsByUrlHash` |
| Validation private method | `require{Condition}` | `requireAdminKey` |
| Entity factory method | `create()` | `ArticleEntity.create(...)` |
| Service input/output model | `{Concept}` (no `Command` suffix) | `SyncPost`, `ArticleEmbeddingResult`, `DocumentEmbedding` |
| Post-call DB writer | `{Feature}Writer` | `ArticleEmbeddingWriter`, `PostEmbeddingWriter` |
| Pre-call DB preflight | `{Feature}Preflight` | `ChatPreflight` |
| Scheduler | `{Feature}Job` | `CrawlJob`, `ArticleEmbeddingJob` |

### Style Rules

- Always use trailing commas
- Never use `!!` — use `?:`, `?.let`, or `requireNotNull` instead
- Extract a `domain/` type when function parameters exceed 4
- Never branch on Boolean parameters — split into separate functions
- Keep branching flat with guard clauses (max 2 levels of nesting)
- Functions must stay under 40 lines
- Log in English using KotlinLogging
- Max line length: 120 characters
- **One type per file.** Application service files contain only `@Service` classes. Models, commands, snapshots, results, statuses live in `domain/`.
- If an application service file grows past ~400 lines, split by **use case** (`XxxAdminService.kt`, `XxxSyncService.kt`), not by extracting a generic `Worker`.

```kotlin
// Bad — top-level data classes inside an application file
// post/application/sync/PostSyncService.kt
@Service class PostSyncService(...) { ... }
data class SyncPost(...)        // ← belongs in post/domain/SyncPost.kt
enum class SyncResult { ... }   // ← belongs in post/domain/SyncResult.kt

// Good — application file holds only the @Service
// post/application/sync/PostSyncService.kt
@Service class PostSyncService(...) { ... }
// post/domain/SyncPost.kt
data class SyncPost(...)
// post/domain/SyncResult.kt
enum class SyncResult { APPLIED, STALE_IGNORED, TOMBSTONED }
```

- Keep `if` conditions plain. Avoid `!`, avoid safe-call chains (`x?.foo() == true`), avoid null comparisons buried in compound conditions. Flatten the value first — with `?: return`, `?: continue`, `takeIf { ... }`, or a named boolean — so the `if` itself reads as a domain concept on a non-nullable receiver. Prefer a positive condition with early return, then handle the failure case after; the happy path reads forward instead of as "not the bad thing." Prefer the positive form of negated extension calls (`isNotBlank()` over `!isNullOrBlank()`, `isNotEmpty()` over `!isEmpty()`).

```kotlin
// Bad — negation buried in a method call
if (!chatSessionRepository.existsById(sessionId)) {
    throw AppException(ErrorCode.SESSION_NOT_FOUND)
}

// Good — Spring Data Kotlin idiom: findByIdOrNull then elvis-throw
chatSessionRepository.findByIdOrNull(sessionId)
    ?: throw AppException(ErrorCode.SESSION_NOT_FOUND)

// Bad — negated extension call, or safe-call chain in the condition
if (!cleaned.isNullOrBlank()) return cleaned
if (cleaned?.isNotBlank() == true) return cleaned

// Good — flatten with `?: continue`/`?: return`, then a plain positive check
val cleaned = contentCleaner.clean(element.html()) ?: continue
if (cleaned.isNotBlank()) return cleaned
```

- Use `?.` (safe-call) sparingly — only when each step in the chain has a genuinely nullable receiver from an external boundary you don't control. Don't paper over branching logic with long `?.foo()?.bar()?.baz()` chains; flatten to non-null at the earliest point with `?: return` / `?: continue`, then operate on the non-nullable value.

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
ApiResponse.success(data)
ApiResponse.success() // for no-body responses

throw AppException(ErrorCode.ARTICLE_NOT_FOUND)
```

### Request DTO Pattern

```kotlin
// post/api/sync/SyncPostRequest.kt
data class SyncPostRequest(
    @field:NotBlank
    @field:Size(max = 64)
    val externalId: String,
    ...
)
```

- One DTO per file in `api/`
- Validation annotations require `@field:` prefix
- Controllers must use `@Valid @RequestBody`

### Response DTO Pattern

```kotlin
// post/api/similar/SimilarResponse.kt
data class SimilarResponse(
    val status: SimilarStatus,
    val items: List<SimilarItem>,
) {
    companion object {
        fun of(result: SimilarResult) = ...
    }
}
```

- One DTO per file in `api/`
- Use `companion object { fun of() }` factory method
- Map from `domain/` types only — never reference entities directly

---

## Entity & Storage Rules

### Entity (`{feature}/infrastructure/{Name}Entity.kt`)

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
- Audit columns via `BaseTimeEntity` from `global/persistence/`

### Mapper (`{feature}/infrastructure/{Feature}Mapper.kt`)

Entity → Domain conversion lives next to the entity:

```kotlin
// blog/infrastructure/BlogMapper.kt
fun BlogEntity.toBlog() =
    Blog(
        id = requireNotNull(id) { "BlogEntity.id must not be null after persistence" },
        ...
    )
```

---

## Persistence

Two persistence patterns coexist:

| Pattern | Use for | Examples |
|---|---|---|
| `JpaRepository<Entity, Id>` interface | Entity-bound CRUD; entities have an `@Entity` mapping | `ArticleRepository`, `BlogRepository`, `PostRepository`, `ChatSessionRepository`, `ChatMessageRepository` |
| Spring Data `@Query` (native) on the JPA repo | Bulk write / multi-table snapshot queries that still belong to an entity | `PostRepository.upsert`, `ArticleRepository.findUnembeddedSnapshots` |
| `@Repository` + `JdbcTemplate` | Native SQL using types JPA doesn't model (`pgvector <=>`, `tsvector`, `korean_bigram_*`, custom JSONB) | `RagChunkRepository`, `RateLimitStore` |

`@Repository` classes that use `JdbcTemplate` live in `{feature}/infrastructure/` alongside Spring Data interfaces. One class per file.

### TIMESTAMPTZ caveat for native queries

When mapping `List<Array<Any>>` rows from `@Query(nativeQuery = true)`, route TIMESTAMPTZ columns through `JdbcTimeMapper.toOffsetDateTime(row[i])` — the driver returns `Instant` in production but `Timestamp` in Testcontainers, and a direct cast crashes one of the two. See `global/persistence/JdbcTimeMapper.kt`.

---

## Database Migration (Flyway)

- Filename: `V{N}__{snake_case_description}.sql`
- Use `IF EXISTS` / `IF NOT EXISTS` for idempotency
- Use `CREATE EXTENSION IF NOT EXISTS` for pgVector

---

## Error Handling

```kotlin
// Define: ErrorCode enum (global/error/ErrorCode.kt)
ARTICLE_NOT_FOUND(HttpStatus.NOT_FOUND, "ARTICLE_001", "Article not found", LogLevel.WARN)

// Throw: AppException
throw AppException(ErrorCode.ARTICLE_NOT_FOUND)

// Catch: ApiControllerAdvice auto-converts to ApiResponse.error()
```

- Add new errors to the `ErrorCode` enum
- Code format: `{DOMAIN}_{number}` (AUTH_001, ARTICLE_001, EMBED_001, CRAWL_001, COMMON_001)
- logLevel: client fault (4xx) → INFO/WARN, server fault (5xx) → ERROR

---

## Job (Scheduler) Rules

Jobs are thin orchestrators. They:
- Call application services to perform work
- Catch and log exceptions
- Never access repositories directly

```kotlin
@Component
class CrawlJob(
    private val crawlService: CrawlService,
) {
    @Scheduled(cron = "0 0 */6 * * *")
    fun fetch() {
        try {
            crawlService.crawlAll()
        } catch (e: Exception) {
            log.error(e) { "Crawl failed" }
        }
    }
}
```

---

## Verification Checklist

After any code change:

1. `./gradlew check` — tests + ktlintCheck + detekt + ArchitectureBoundaryTest pass
2. New feature or bugfix → add tests
3. Schema change → add Flyway migration
4. New error code → add to `ErrorCode` enum
5. New admin endpoint → validate `X-Admin-Key` header
6. New architectural rule → extend `ArchitectureBoundaryTest`, do not rely on convention docs alone

---

## Architecture guardrails

`src/test/kotlin/com/blog/ai/ArchitectureBoundaryTest.kt` enforces two rules at test time. They run as part of `./gradlew check` and any violation fails the build:

| Rule | Forbidden pattern | Reason |
|---|---|---|
| No legacy structure | `com.blog.ai.core`, `com.blog.ai.storage`, `Committer`, `CommitCommand` (anywhere in `src/main/`) | The pre-PR1 shape and the `*Committer`/`*CommitCommand` vocabulary must stay retired |
| `RagChunkRepository` is rag-internal | `RagChunkRepository` referenced from any file outside `com/blog/ai/rag/` | Cross-feature callers must use `RagSearchService` (read) or `RagWriteService` (write); the JdbcTemplate repository is a feature-internal detail |

When introducing a new convention that can be expressed as a regex/import check, **extend this test instead of (or in addition to) writing a doc rule**. A test fails CI; a doc rule does not.

The `RagSearchService` / `RagWriteService` split is a CQRS-ish boundary for the one place where read and write paths have meaningfully different fan-in. Other features keep a single `*Service` until that pressure shows up — splitting prematurely is not the convention.

---

## What NOT To Do

- Reintroduce a top-level `core/`, `storage/`, or `scheduler/` package
- Reintroduce per-feature `controller/request/response/service/entity/repository/model/mapper` flat-template sub-packages (retired)
- Place a top-level `data class` or `enum class` inside an `application/` file (move to `domain/`)
- Access another feature's `infrastructure/` from outside that feature
- Return entities directly as responses
- Call repositories from controllers or jobs
- Pass Entity objects as application service parameters
- Write operations without `@Transactional`
- Log API keys, tokens, or secrets
- Use `@Async` on self-calls (extract to separate `@Service`)
- Inline external-call writers/preflights into the calling service (breaks the `@Transactional` proxy boundary)
- Swallow errors with `runCatching { }.getOrDefault()`
- Declare work complete without running `./gradlew check`
- Mix a structure-move PR with a behavior-change PR
- Introduce new patterns without discussion
