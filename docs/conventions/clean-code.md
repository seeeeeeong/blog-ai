# Clean Code Guide

## Goal

Clean code in these repositories means code that makes the next change cheap.
The priority order is:

1. Preserve behavior and domain rules.
2. Make intent obvious at the call site.
3. Keep boundaries explicit between HTTP, application logic, and persistence.
4. Prefer small, reversible refactors over large rewrites.

## Structure Standard

Both `blog-api` and `blog-ai` follow the same feature-first shape with layered sub-packages. **One type per file.**

```text
src/main/kotlin/com/blog/{api|ai}
├── {Application}.kt
├── global
│   ├── admin
│   ├── config
│   ├── error
│   ├── jdbc
│   ├── jpa
│   ├── properties
│   ├── response
│   └── text             # ai-only: text/embedding utilities
├── {feature}/
│   ├── controller       # @RestController (one per file)
│   ├── request          # Request DTOs (one type per file)
│   ├── response         # Response DTOs (one type per file)
│   ├── service          # @Service use-case classes (no top-level data classes)
│   ├── entity           # @Entity JPA classes (one entity per file)
│   ├── repository       # Spring Data interfaces, @Repository JdbcTemplate, or @Repository jOOQ DSLContext classes
│   ├── model            # Domain models, command inputs, results, statuses (one type per file)
│   ├── mapper           # Entity.toDomain() extension functions
│   ├── client           # External HTTP/SDK clients (optional)
│   ├── parser           # Parsers (e.g., RSS) (optional)
│   ├── support          # Stateless helpers, constants (optional)
│   ├── retriever        # Spring AI DocumentRetriever (optional)
│   └── memory           # Spring AI ChatMemory impls (optional)
└── scheduler
    └── {Feature}Job.kt  # @Scheduled orchestrators (thin)

src/test/kotlin/com/blog/{api|ai}    # mirrors main package paths
config/detekt/detekt.yml
docs/conventions/clean-code.md
```

Top-level `core/` and `storage/` packages are forbidden — persistence belongs inside the owning feature as `entity/` + `repository/`. Project-specific cross-cutting concerns (security, auth, web filters) live as sub-packages of `global/`. AI-only packages (`rag/`, `chat/retriever/`, `chat/memory/`) live as feature sub-packages, never under a parallel top-level wrapper.

### File grouping rules

- **One type per file.** A `data class`, `enum class`, or `interface` always lives in its own file named after the type.
- `service/` files contain **only** `@Service` (or `@Component`) classes. No top-level `data class` / `enum class` is permitted there — those belong in `model/`.
- `entity/` files contain a single `@Entity` JPA class.
- `repository/` files contain a single Spring Data interface, `@Repository` JdbcTemplate class, or `@Repository` jOOQ-DSLContext class. Pick by data access shape — entity-bound CRUD → JpaRepository; type-safe queries on codegen-included tables → jOOQ DSLContext; queries using types jOOQ doesn't model (`pgvector <=>`, `tsvector`) → jOOQ plain-SQL templating or `JdbcTemplate`.
- `controller/` files contain a single `@RestController`. Multiple controllers per feature → multiple files in `controller/`.
- `request/` and `response/` hold one DTO per file. Validation annotations on request DTOs use `@field:` prefix.
- `mapper/` files hold extension functions (typically `Entity.toDomain()`) for one entity. Multiple entities → multiple mapper files.
- Cross-feature contract types (`RagChunkHit`, `RagSourceType`, etc.) own their file inside the producing feature's `model/`.

### Service decomposition

External-API + DB write boundaries are preserved as **separate Spring beans**, not inlined into the calling service:

| Suffix | Role |
|---|---|
| `{Feature}Service` | use-case orchestrator |
| `{Feature}Worker` | per-item processor (kept separate when called inside an `@Async` or batch loop) |
| `{Feature}Writer` | post-external-call DB write, runs in its own `@Transactional` so the caller's txn does not span the network round-trip |
| `{Feature}Preflight` | pre-external-call DB read/write, same boundary intent (callers run external work after) |

If `XxxService.kt` grows past ~400 lines, split by **use case** (`XxxAdminService.kt`, `XxxSyncService.kt`), not by extracting a generic helper.

## Design Rules

### Favor stable boundaries

- Controllers translate HTTP to commands and responses.
- Services orchestrate use cases and transactions.
- Entities and value objects protect invariants.
- Repositories hide persistence details.

If a type depends on Spring MVC or servlet APIs, it stays in `{feature}/controller/` or under `global/web/`.

### Prefer intention over cleverness

- Names should explain why the code exists.
- Avoid utility classes that mix unrelated concerns.
- Inline trivial indirection; extract only when the name clarifies behavior.

### Keep functions focused

- A function should do one business step at one abstraction level.
- Use guard clauses to keep branching flat.
- Four parameters is already a smell. Introduce a model object (in `model/`) when the call site becomes unclear.
- Boolean flags are acceptable only when they model a real domain concept. If they switch behavior, split the function.

### Nullability is a design signal

- Use non-null by default.
- Return nullable only when absence is a valid outcome.
- Resolve nullable values at the boundary where the decision becomes meaningful.
- Never use `!!`.

### Entities are not DTOs

- Do not expose JPA entities from controllers.
- Keep persistence annotations inside `{feature}/entity/`.
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
- Top-level `data class` / `enum class` inside a `service/` file
- Cross-feature imports of another feature's `entity/` or `repository/` — when blog-ai introduces a hard rule (e.g., `rag.repository` is rag-internal), enforce it in `ArchitectureBoundaryTest`, not just in this doc
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
- domain terms are consistent across controller, service, entity, and model,
- static analysis and formatting can run from Gradle,
- a new contributor can tell where to place the next feature without guessing.
