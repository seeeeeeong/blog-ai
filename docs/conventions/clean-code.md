# Clean Code Guide

## Goal

Clean code in these repositories means code that makes the next change cheap.
The priority order is:

1. Preserve behavior and domain rules.
2. Make intent obvious at the call site.
3. Keep boundaries explicit between HTTP, application logic, and persistence.
4. Prefer small, reversible refactors over large rewrites.

## Structure Standard

> **Status:** Target shape, post-PR1. Legacy code in both repos still uses `core/api`, `core/domain`, `core/support`, `storage/`. PR0 updates these conventions only; PR1 performs the move.

Both `blog-api` and `blog-ai` follow the same feature-first shape:

```text
src/main/kotlin/com/blog/{api|ai}
├── {Application}.kt
├── global
│   ├── config
│   ├── error
│   ├── response
│   ├── properties
│   ├── text             # ai-only: text/embedding utilities
│   └── jdbc
├── {feature}/           # one package per feature: article, blog, crawl, chat, post, rag, ...
│   ├── XxxService.kt    # use-case service (+ private data classes)
│   ├── XxxApi.kt        # @RestController(s) + Request/Response DTOs
│   ├── XxxStore.kt      # @Entity + Repository + persistence Commands + extensions
│   ├── XxxClient.kt     # optional — external HTTP/SDK client
│   └── XxxPreflight.kt  # optional — DB work guarding an external call
└── scheduler
    └── XxxJob.kt        # @Scheduled orchestrators (thin)

src/test/kotlin/com/blog/{api|ai}
config/detekt/detekt.yml
docs/conventions/clean-code.md
```

Top-level `core/` and `storage/` packages are forbidden — persistence belongs inside the owning feature as `XxxStore.kt`. Project-specific cross-cutting concerns (security, auth, web filters) live as sub-packages of `global/`. AI-only packages (`rag/`, `chat/retriever/...`) live as feature packages, never under a parallel top-level wrapper.

### File grouping rules

- A feature flow lives in 2–5 files. Promote a type to its own file only when a second feature imports it.
- `XxxService.kt` may co-locate private Snapshot/Batch/scoped Command data classes.
- `XxxStore.kt` may co-locate multiple `@Entity` and `Repository` declarations belonging to the feature, plus persistence Commands and entity extensions.
- `XxxApi.kt` may hold more than one `@RestController` if their routes belong to the same feature surface (e.g., `post/PostApi.kt` holds both `InternalPostController` and `SimilarPostController`).
- Domain models (`Article`, `Blog`, `Post`) and cross-package contract types (`RagChunkHit`, `RagSourceType`) own their file.
- If `XxxService.kt` grows past ~400 lines, split by **use case** (`XxxAdminService.kt`, `XxxSyncService.kt`), not by extracting a `Committer`/`Worker`.

## Design Rules

### Favor stable boundaries

- Controllers translate HTTP to commands and responses.
- Services orchestrate use cases and transactions.
- Entities and value objects protect invariants.
- Repositories hide persistence details.

If a type depends on Spring MVC or servlet APIs, it stays in a feature's `XxxApi.kt` or under `global/web`.

### Prefer intention over cleverness

- Names should explain why the code exists.
- Avoid utility classes that mix unrelated concerns.
- Inline trivial indirection; extract only when the name clarifies behavior.

### Keep functions focused

- A function should do one business step at one abstraction level.
- Use guard clauses to keep branching flat.
- Four parameters is already a smell. Introduce a command or value object when the call site becomes unclear.
- Boolean flags are acceptable only when they model a real domain concept. If they switch behavior, split the function.

### Nullability is a design signal

- Use non-null by default.
- Return nullable only when absence is a valid outcome.
- Resolve nullable values at the boundary where the decision becomes meaningful.
- Never use `!!`.

### Entities are not DTOs

- Do not expose JPA entities from controllers.
- Keep persistence annotations inside `storage`.
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
- Request/response DTOs leaking into services
- Stringly typed status, role, or event identifiers
- Repeated mapping code with no single owner
- Top-level packages outside `global/`, the per-feature packages, or `scheduler/`
- Cross-feature imports of another feature's `XxxStore`
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
- domain terms are consistent across controller, service, and storage,
- static analysis and formatting can run from Gradle,
- a new contributor can tell where to place the next feature without guessing.

