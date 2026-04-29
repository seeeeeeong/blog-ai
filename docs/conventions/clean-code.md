# Clean Code Guide

## Goal

Clean code in these repositories means code that makes the next change cheap.
The priority order is:

1. Preserve behavior and domain rules.
2. Make intent obvious at the call site.
3. Keep boundaries explicit between HTTP, application logic, and persistence.
4. Prefer small, reversible refactors over large rewrites.

## Structure Standard

Both `blog-api` and `blog-ai` follow **feature-first with DDD-style layers** inside each feature: `api / application / domain / infrastructure`. **One type per file.**

```text
src/main/kotlin/com/blog/{api|ai}
├── {Application}.kt
├── global/                      # cross-cutting infra (admin, config, error, persistence, properties, response, text)
├── {feature}/
│   ├── api/                     # @RestController + Request/Response DTOs (only if HTTP surface)
│   ├── application/             # @Service use-case classes
│   │   └── {sub-concern}/       # split application/ further by concern when 4+ files (e.g., embedding/, retrieval/, ratelimit/)
│   ├── domain/                  # domain models, command inputs, results, statuses
│   └── infrastructure/          # @Entity + Repository + mappers + external clients (HTTP/SDK)
│       └── {sub-concern}/       # split infrastructure/ further when needed (e.g., parser/, rerank/)
└── scheduler/
    └── {Feature}Scheduler.kt    # @Scheduled orchestrators (thin)

src/test/kotlin/com/blog/{api|ai}    # mirrors main package paths
config/detekt/detekt.yml
docs/conventions/clean-code.md
```

Apply layers only where the feature has enough files to justify them. A feature without HTTP endpoints (e.g., `article`, `crawl`, `rag`) skips `api/`. A tiny feature (`blog`) still keeps `application/domain/infrastructure` for consistency.

### Layer rules

| Layer | Holds |
|---|---|
| `api/` | `@RestController` + Request/Response DTOs (one type per file, `@field:` validation) |
| `application/` | `@Service` use-case classes — **no** top-level `data class` / `enum class` |
| `domain/` | Domain models, command inputs, results, statuses (one type per file) |
| `infrastructure/` | `@Entity` JPA classes, Spring Data interfaces, `@Repository` JdbcTemplate classes, mappers, external HTTP/SDK clients |

Sub-folders inside a layer split that layer further by concern (e.g., `application/embedding/`, `infrastructure/parser/`). Use them when a layer has 4+ tightly-related files.

### Service decomposition

External-API + DB write boundaries are preserved as **separate Spring beans**, never inlined:

| Suffix | Role |
|---|---|
| `{Feature}Service` | use-case orchestrator |
| `{Feature}Worker` | per-item processor (`@Async`/batch) |
| `{Feature}Writer` | post-external-call DB write (own `@Transactional`) |
| `{Feature}Preflight` | pre-external-call DB read/write |

If a service grows past ~400 lines, split by **use case** (`XxxAdminService.kt`, `XxxSyncService.kt`), not by extracting a generic helper.

### File grouping rules

- One type per file. A `data class`, `enum class`, or `interface` always lives in its own file named after the type.
- Application service files contain only `@Service` classes. No top-level `data class` / `enum class` — those go in `domain/`.
- Entity, repository, mapper live together in `infrastructure/` (or its sub-folder when split).
- Validation annotations on request DTOs use `@field:` prefix.
- Cross-feature contract types (`RagChunkHit`, `RagSourceType`, etc.) live in the producing feature's `domain/`.

### Forbidden

- Top-level `core/` or `storage/` packages
- Per-feature `controller/request/response/service/entity/repository/model/mapper` flat-template sub-packages (retired)
- Top-level `data class` / `enum class` declared inside an `application/` file
- Cross-feature imports of another feature's `infrastructure/` — hard rules (e.g., `RagChunkRepository` is rag-internal) belong in `ArchitectureBoundaryTest`, not just in this doc

## Design Rules

### Favor stable boundaries

- Controllers translate HTTP to commands and responses.
- Application services orchestrate use cases and transactions.
- Entities and value objects protect invariants.
- Repositories hide persistence details.

If a type depends on Spring MVC or servlet APIs, it stays in `{feature}/api/` or under `global/web/`.

### Prefer intention over cleverness

- Names should explain why the code exists.
- Avoid utility classes that mix unrelated concerns.
- Inline trivial indirection; extract only when the name clarifies behavior.

### Keep functions focused

- A function should do one business step at one abstraction level.
- Use guard clauses to keep branching flat.
- Four parameters is already a smell. Introduce a `domain/` value object when the call site becomes unclear.
- Boolean flags are acceptable only when they model a real domain concept. If they switch behavior, split the function.

### Nullability is a design signal

- Use non-null by default.
- Return nullable only when absence is a valid outcome.
- Resolve nullable values at the boundary where the decision becomes meaningful.
- Never use `!!`.

### Entities are not DTOs

- Do not expose JPA entities from controllers.
- Keep persistence annotations inside `{feature}/infrastructure/`.
- Put state-changing behavior behind methods when invariants matter.
- Factory methods are preferred, but entity construction must still respect JPA requirements.

### Logging is for operations

- Log in English.
- Log facts and identifiers, not guesses.
- Avoid duplicate exception logging across layers.
- Never log secrets, tokens, passwords, or raw credentials.

## Review Heuristics

These are strong review rules, not absolute dogma:

- Nested conditions deeper than two levels
- Functions longer than ~40 lines
- Mutable shared state
- Request/response DTOs leaking into application services
- Stringly typed status, role, or event identifiers
- Repeated mapping code with no single owner
- Top-level packages outside `global/`, the per-feature packages, or `scheduler/`
- Top-level `data class` / `enum class` inside an `application/` file
- Cross-feature imports of another feature's `infrastructure/`
- Mixing structure moves with behavior changes in one PR

When breaking one of these rules improves clarity, document the reason in the PR or commit message.

## Refactoring Policy

When changing existing code:

1. Keep package moves small and mechanical.
2. Do not mix large renames with behavior changes.
3. Prefer adding missing structure and guardrails before deep domain rewrites.
4. Preserve in-flight user work. If a file is already being edited, avoid unrelated rewrites.

## Done Criteria

A change is considered clean enough when:

- the main path reads top-down without jumping across layers,
- domain terms are consistent across `api`, `application`, `domain`, and `infrastructure`,
- static analysis and formatting can run from Gradle,
- a new contributor can tell where to place the next feature without guessing.
