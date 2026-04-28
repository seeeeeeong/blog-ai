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

Feature-first. **One type per file.** Each feature picks the shape that fits its domain — vertical slice (use-case sub-packages), functional cohesion, or flat — instead of forcing every feature into the same layered template.

```
com.blog.ai
├── BlogAiApplication.kt
│
├── global                          # cross-cutting infra (always sub-packaged by concern)
│   ├── admin                       # operational REST endpoints (AdminController)
│   ├── config                      # @Configuration beans
│   ├── error                       # AppException, ErrorCode, ErrorMessage, ApiControllerAdvice
│   ├── jdbc                        # JdbcTimeMapper
│   ├── jpa                         # BaseTimeEntity (@MappedSuperclass)
│   ├── properties                  # @ConfigurationProperties holders
│   ├── response                    # ApiResponse<T>, ResultStatus
│   └── text                        # TextSplitter, TokenTruncator, EmbeddingBatcher
│
├── article                         # entity + admin at root, one use case sub-packaged
│   ├── ArticleEntity, ArticleRepository, ArticleAdminService
│   └── embedding                   # ArticleEmbeddingService, Writer, Snapshot, Result
│
├── blog                            # flat — only 5 files, no sub-packages needed
│   └── Blog, BlogEntity, BlogRepository, BlogMapper, BlogCacheService
│
├── crawl                           # services at root, parsing pipeline grouped
│   ├── CrawlService, CrawlAsyncService, ArticleSaveService, CrawlConstants
│   └── parser                      # RssFeedParser, WebContentScraper, ContentCleaner, ParsedArticle
│
├── chat                            # main chat surface at root, functional cohesion sub-packages
│   ├── ChatController, ChatRequest, ChatMessageResponse, ChatSessionResponse, ChatService
│   ├── session                     # ChatSessionService + Entity + Repository
│   ├── memory                      # ChatMemoryStore + ChatMessage + MessageEntity + Repository + Mapper
│   ├── rag                         # ArticleRetriever + QueryPlanner + QueryExpander +
│   │                               #   ClarificationService + RerankClient + chat-RAG models
│   └── ratelimit                   # RateLimiter + ChatPreflight + RateLimitStore + Request + Outcome
│
├── post                            # entity at root, vertical slice per use case
│   ├── PostEntity, PostRepository
│   ├── sync                        # InternalPostController + DTOs + PostSyncService + SyncPost + SyncResult + EventType
│   ├── embedding                   # PostEmbeddingService + Writer + Snapshot + Result
│   └── similar                     # SimilarPostController + DTOs + SimilarPostService + SimilarArticle + Result + Status
│
├── rag                             # services + repository + types at root, embedding pipeline grouped
│   ├── RagSearchService, RagWriteService, ChunkEnricher
│   ├── RagChunkRepository (rag-internal — see Architecture guardrails)
│   ├── RagSourceType, RagChunkGranularity, RagSearchQuery, RagChunkWrite, RagChunkHit
│   └── embedding                   # EmbeddingPipeline + EmbeddingDocument + DocumentEmbedding + ChunkEmbedding + ChunkEmbeddingJob
│
└── scheduler                       # *Job.kt — thin @Scheduled orchestrators
```

### Picking a feature shape

The `controller/request/response/service/entity/repository/model/mapper` layered template **was retired** in PR22-24. It scattered each use case across 5+ packages and produced over-fragmented `model/` folders. Pick by domain shape instead:

| Domain shape | Pattern | Used by |
|---|---|---|
| Multiple distinct use cases sharing one entity | **Vertical slice** — entity at root, one sub-package per use case | `post/` (sync, embedding, similar) |
| One main flow with clearly distinct supporting concerns | **Functional cohesion** — main surface at root, sub-packages by concern | `chat/` (session, memory, rag, ratelimit) |
| Single use case + entity | Entity + admin/related at root, single use-case sub-package | `article/` (embedding) |
| Small / single concern | **Flat** — everything at feature root, no sub-packages | `blog/`, `rag/`, `crawl/` (mostly flat with `parser/` grouped) |

**Choose the shape from the actual domain, not from a template.** Different features deliberately have different shapes.

### File-level rules (still apply)

- One type per file. `service` files contain only `@Service` (or `@Component`) classes — no top-level `data class` / `enum class`.
- Domain models, command inputs, result/status types live next to their owning use case (in the relevant sub-package), not in a global `model/` folder.
- `Entity.toDomain()` extension functions live next to the entity (or in the same sub-package), not in a separate `mapper/` folder.

### Service decomposition

External-API + DB write boundaries are preserved as **separate Spring beans**, never inlined:

| Suffix | Role |
|---|---|
| `{Feature}Service` | use-case orchestrator |
| `{Feature}Worker` | per-item processor (separate `@Service` for `@Async`/batch loops) |
| `{Feature}Writer` | post-external-call DB write — own `@Transactional`, txn does not span the round-trip |
| `{Feature}Preflight` | pre-external-call DB read/write — same boundary intent |

`scheduler/{Feature}Job.kt` for `@Scheduled` orchestrators (one job per file).

**Never do:**
- Reintroduce a top-level `core/` or `storage/` package
- Reintroduce the layered `controller/request/response/service/entity/repository/model/mapper` sub-packages template (retired in PR22-24)
- Put a top-level `data class` or `enum class` inside a service file (move to the relevant model location)
- Cross-feature imports of another feature's entity or repository. Specifically: `RagChunkRepository` is **rag-internal** — outside callers must go through `RagSearchService` (read) or `RagWriteService` (write). This is enforced by `ArchitectureBoundaryTest` (see [Architecture guardrails](#architecture-guardrails)).
- Access an entity/repository from a controller or scheduler — always go through a domain service
- Cross-feature imports for anything except `global/*` and `rag/*` shared types
- Expose JPA entities in controller responses or domain service parameters
- Pass Entity objects between domain services (use domain models or IDs)
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
- Extract a `model/` type when function parameters exceed 4
- Never branch on Boolean parameters — split into separate functions
- Keep branching flat with guard clauses (max 2 levels of nesting)
- Functions must stay under 40 lines
- Log in English using KotlinLogging
- Max line length: 120 characters
- **One type per file.** Service files contain only `@Service` classes. Models, commands, snapshots, results, statuses live in `{feature}/model/`.
- If a service file grows past ~400 lines, split by **use case** (`XxxAdminService.kt`, `XxxSyncService.kt`), not by extracting a generic `Worker`.

```kotlin
// Bad — top-level data classes inside a service file
// post/service/PostSyncService.kt
@Service class PostSyncService(...) { ... }
data class SyncPost(...)        // ← belongs in post/model/SyncPost.kt
enum class SyncResult { ... }   // ← belongs in post/model/SyncResult.kt

// Good — service file holds only the @Service
// post/service/PostSyncService.kt
@Service class PostSyncService(...) { ... }
// post/model/SyncPost.kt
data class SyncPost(...)
// post/model/SyncResult.kt
enum class SyncResult { APPLIED, STALE_IGNORED, TOMBSTONED }
```

- Keep `if` conditions plain. Avoid `!`, avoid safe-call chains (`x?.foo() == true`), avoid null comparisons buried in compound conditions. Flatten the value first — with `?: return`, `?: continue`, `takeIf { ... }`, or a named boolean — so the `if` itself reads as a domain concept on a non-nullable receiver. Prefer a positive condition with early return, then handle the failure case after; the happy path reads forward instead of as "not the bad thing." Prefer the positive form of negated extension calls (`isNotBlank()` over `!isNullOrBlank()`, `isNotEmpty()` over `!isEmpty()`).

```kotlin
// Bad — negation buried in a method call
if (!chatSessionRepository.existsById(sessionId)) {
    throw AppException(ErrorCode.SESSION_NOT_FOUND)
}

// Good — name the boolean, branch positively, throw after
val sessionExists = chatSessionRepository.existsById(sessionId)
if (sessionExists) return
throw AppException(ErrorCode.SESSION_NOT_FOUND)

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

// Errors — throw AppException, ApiControllerAdvice handles conversion
throw AppException(ErrorCode.ARTICLE_NOT_FOUND)
```

### Request DTO Pattern

```kotlin
// post/request/SyncPostRequest.kt
data class SyncPostRequest(
    @field:NotBlank
    @field:Size(max = 64)
    val externalId: String,
    ...
)
```

- One DTO per file in `request/`
- Validation annotations require `@field:` prefix
- Controllers must use `@Valid @RequestBody`

### Response DTO Pattern

```kotlin
// post/response/SimilarResponse.kt
data class SimilarResponse(
    val status: SimilarStatus,
    val items: List<SimilarItem>,
) {
    companion object {
        fun of(result: SimilarResult) = ...
    }
}
```

- One DTO per file in `response/`
- Use `companion object { fun of() }` factory method
- Map from domain models only — never reference entities directly

---

## Entity & Storage Rules

### Entity (lives at feature root or in the relevant sub-package, not under a `entity/` folder)

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
- Audit columns via `BaseTimeEntity` from `global/jpa/`

### Mapper

Entity → Domain conversion lives in the same package as the entity (typically `{feature}/{Feature}Mapper.kt` or under the relevant sub-package):

```kotlin
// blog/BlogMapper.kt — entity and mapper share blog/ since blog/ is flat
fun BlogEntity.toBlog() =
    Blog(
        id = requireNotNull(id) { "BlogEntity.id must not be null after persistence" },
        ...
    )

// chat/memory/ChatMessageMapper.kt — entity, repository, mapper all in chat/memory/
fun ChatMessageEntity.toMessage(): ChatMessage = ...
```

---

## Persistence

Three persistence layers coexist. Pick by **data access shape**, not by team preference:

| Pattern | Use for | Examples |
|---|---|---|
| `JpaRepository<Entity, Id>` interface | Entity-bound CRUD; entities have an `@Entity` mapping | `ArticleRepository`, `BlogRepository`, `PostRepository`, `ChatSessionRepository`, `ChatMessageRepository` |
| Spring Data `@Query` (native) on the JPA repo | Bulk write / multi-table snapshot queries that still belong to an entity | `PostRepository.upsert`, `ArticleRepository.findUnembeddedSnapshots` |
| jOOQ `DSLContext` + generated table references | Type-safe SELECT/DELETE/UPDATE on tables included in codegen | `RagChunkRepository.deleteSource`, `RagChunkRepository.findDocumentVector` (uses `RAG_CHUNKS.SOURCE_TYPE` etc.) |
| jOOQ `DSL.sql(...)` / plain SQL templating | Queries using types jOOQ doesn't model (`pgvector <=>`, `tsvector`, `korean_bigram_tsquery`) | `RagChunkRepository.searchHybrid`, `RagChunkRepository` insert |

`@Repository` classes that use `DSLContext` (jOOQ) or `JdbcTemplate` live alongside the entity / Spring Data interface — at feature root or in the relevant sub-package. One class per file.

### jOOQ codegen workflow

Codegen is scoped to specific tables. Currently included: `rag_chunks`.

To add a table to codegen:

1. Update `build.gradle.kts` → `jooq.configuration.generator.database.includes` to add the table name (regex).
2. Set DB env vars: `DB_HOST`, `DB_USERNAME`, `DB_PASSWORD` (defaults `localhost`/`postgres`/`postgres`).
3. Ensure local Postgres has the latest migrations applied.
4. Run `./gradlew jooqCodegen`.
5. Commit the new files under `src/generated/kotlin/`.

When a Flyway migration changes a codegen target (column add/drop/rename, type change), regenerate and commit in the same PR as the migration.

`src/generated/kotlin/` is included in the Kotlin `main` source set but excluded from ktlint and detekt — never edit it by hand.

---

## Database Migration (Flyway)

- Filename: `V{N}__{snake_case_description}.sql`
- Use `IF EXISTS` / `IF NOT EXISTS` for idempotency
- Use `CREATE EXTENSION IF NOT EXISTS` for pgVector
- If the migration changes a jOOQ codegen target table (currently `rag_chunks`), run `./gradlew jooqCodegen` and commit `src/generated/kotlin/` updates in the same PR

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

## Scheduler Rules

Schedulers are thin orchestrators. They:
- Call domain services to perform work
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

1. `./gradlew check` — tests + ktlintCheck + detekt pass
2. New feature or bugfix → add tests
3. Schema change → add Flyway migration (and regen jOOQ codegen if it touches `rag_chunks`)
4. New error code → add to `ErrorCode` enum
5. New admin endpoint → validate `X-Admin-Key` header
6. New architectural rule → extend `ArchitectureBoundaryTest`, do not rely on convention docs alone

---

## Architecture guardrails

`src/test/kotlin/com/blog/ai/ArchitectureBoundaryTest.kt` enforces two rules at test time. They run as part of `./gradlew check` and any violation fails the build:

| Rule | Forbidden pattern | Reason |
|---|---|---|
| No legacy structure | `com.blog.ai.core`, `com.blog.ai.storage`, `Committer`, `CommitCommand` (anywhere in `src/main/`) | The old `core/api`/`storage/` shape and the `*Committer`/`*CommitCommand` vocabulary must stay retired |
| `RagChunkRepository` is rag-internal | `RagChunkRepository` referenced from any file outside `com/blog/ai/rag/` | Cross-feature callers must use `RagSearchService` (read) or `RagWriteService` (write); the JdbcTemplate/jOOQ repository is a feature-internal detail |

When introducing a new convention that can be expressed as a regex/import check, **extend this test instead of (or in addition to) writing a doc rule**. A test fails CI; a doc rule does not.

The `RagSearchService` / `RagWriteService` split is a CQRS-ish boundary for the one place where read and write paths have meaningfully different fan-in. Other features keep a single `*Service` until that pressure shows up — splitting prematurely is not the convention.

---

## What NOT To Do

- Reintroduce a top-level `core/` or `storage/` package
- Reintroduce the layered `controller/request/response/service/entity/repository/model/mapper` template (retired in PR22-24)
- Place a top-level `data class` or `enum class` inside a service file (move to the relevant model location)
- Access another feature's entity or repository directly — go through that feature's domain service
- Return entities directly as responses
- Call repositories from controllers or schedulers
- Pass Entity objects as domain service parameters
- Write operations without `@Transactional`
- Log API keys, tokens, or secrets
- Use `@Async` on self-calls (extract to separate `@Service`)
- Inline external-call writers/preflights into the calling service (breaks the `@Transactional` proxy boundary)
- Swallow errors with `runCatching { }.getOrDefault()`
- Declare work complete without running `./gradlew check`
- Mix a structure-move PR with a behavior-change PR
- Introduce new patterns without discussion
