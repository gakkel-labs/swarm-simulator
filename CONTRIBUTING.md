# Contributing

Solo project, written to stay readable and extensible.

---

## How to run locally

See the [Quickstart in README.md](README.md#quickstart--run-the-demo-in-2-minutes):
start the Java backend with Maven, open the Unity client, hit Play.
No database, no external services — only port `50051` must be free.

---

## Coding conventions

**Java (backend)**
- Java 21 — records, sealed classes, pattern matching are welcome.
- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html): 4-space indent, 100-column limit.
- No Spring Boot: `main()` + `ScheduledExecutorService` + `ServerBuilder` only.
- Tests: JUnit 5 + Mockito, AAA pattern, descriptive method names.
- Variable names must be explicit — no single-letter or abbreviated identifiers.

**Protobuf** — definitions in `contracts/`, proto3, snake_case fields. Do not break the `WorldState` streaming contract without bumping the package version.

**Commits** — [Conventional Commits](https://www.conventionalcommits.org/):
```
feat(boids): add lateral avoidance when obstacles are flanked
fix(grpc): close stream cleanly on server shutdown
docs(readme): add Unity setup screenshots
```
Valid scopes: `boids`, `grpc`, `agent`, `unity`, `proto`, `ci`, `docs`.

---

## Pull request process

1. CI must be green (Maven build + JUnit tests) before merge.
2. Prefer small, focused PRs — one concern per PR.
3. Reference the issue in the description (`Closes #N`).
4. No WIP merges — draft PRs are fine to share early, merge only when ready.

---

## Reporting issues

Include: what you expected, what happened (stack trace / log / Unity console error), reproduction steps (JDK version, Unity version, OS), and a label (`bug`, `enhancement`, or `question`).

For design or architecture questions, open a Discussion instead.
