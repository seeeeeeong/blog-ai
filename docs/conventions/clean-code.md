# Clean Code Guide

## Goal

Clean code in these repositories means code that makes the next change cheap.
The priority order is:

1. Preserve behavior and domain rules.
2. Make intent obvious at the call site.
3. Keep boundaries explicit between HTTP, application logic, and persistence.
4. Prefer small, reversible refactors over large rewrites.

## Structure Standard

Both `blog-api` and `blog-ai` follow the same repository shape:

```text
src/main/kotlin/com/blog/{api|ai}
├── core
│   ├── api
│   │   ├── config
│   │   └── controller/v1
│   │       ├── request
│   │       └── response
│   ├── domain
│   │   └── {bounded-context}
│   └── support
│       ├── error
│       ├── properties
│       └── response
├── scheduler            # optional
└── storage
    └── {bounded-context}

src/test/kotlin/com/blog/{api|ai}
config/detekt/detekt.yml
docs/conventions/clean-code.md
```

Project-specific packages such as `support/security`, `support/auth`, `support/web`, or AI-only scheduler packages are allowed.
Add them under the same branch rather than inventing a parallel top-level package.

## Design Rules

### Favor stable boundaries

- Controllers translate HTTP to commands and responses.
- Services orchestrate use cases and transactions.
- Entities and value objects protect invariants.
- Repositories hide persistence details.

If a type depends on Spring MVC or servlet APIs, it stays in `core/api` or `core/support/web`.

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
- Top-level packages that bypass `core`, `storage`, or `scheduler`

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

