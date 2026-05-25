# ADR-0002: No Spring Boot for the MVP backend

## Status
Accepted — 2026-01-15 (retroactive, formalized 2026-05-25)

## Context
The `swarm-server` backend is a Java server that hosts the Boids simulation loop and exposes
a gRPC service. The application skeleton needs to be decided.

The author knows Spring Boot and uses it on other professional projects. The default
temptation would be to use the `spring-boot-starter` + `grpc-spring-boot-starter` because
it is familiar. But this project has specific constraints:

- **Frugal MVP**: v0.1 is a single-process simulator, no database, no authentication
  (see CLAUDE.md), a single `port 50051` for gRPC.
- **Demo runnable in 2 minutes**: explicit goal of the project. `java -jar swarm-server.jar`
  must be enough — no profile, no Spring config, no bean refresh wait.
- **Pedagogical reading**: a developer discovering the repo must be able to follow the wiring
  linearly. Spring hides the wiring behind annotations and auto-config.
- **Footprint and startup time**: Spring Boot adds ~15 MB and 2-3 s of cold start. On
  Raspberry Pi (v0.3), this is not negligible.
- **Components to wire**: a `World`, a `SimulationLoop` (ScheduledExecutorService 30 Hz),
  a broadcaster (ScheduledExecutorService 20 Hz), a gRPC `ServerBuilder` with 3 services.
  All of this fits in < 60 lines inside a `main()`.

## Decision
- No Spring Boot, no Micronaut, no Quarkus, **no DI framework** for the MVP.
- Skeleton = **`SwarmServer.main()`** (`swarm-server/.../server/SwarmServer.java`) delegating
  the wiring to **`SwarmServerBootstrap.create(port)`** — a static composition class,
  readable top to bottom.
- Lifecycle: `ServerBuilder.forPort(50051).build().start()` + `Runtime.addShutdownHook()`
  for graceful shutdown. The `ScheduledExecutorService` instances are created with a
  `ThreadFactory` that names threads (`sim-loop`, `swarm-broadcaster`) and marks them
  `daemon`.
- If a non-trivial external dependency appears later (database, externalized config,
  Prometheus metrics, dev/prod profiles), we will reconsider introducing a framework —
  but via a new ADR that supersedes this one.

## Consequences
+ **Instant startup**: `main()` → server ready in ~200 ms, ideal for the demo and for
  integration tests that will spin the server in-process.
+ **Explicit, linear wiring**: `SwarmServerBootstrap` reads top to bottom. No IoC magic,
  no bean cycle to debug.
+ **Minimal memory and binary footprint**: relevant in preparation for the Pi agent (v0.3).
+ **Easy testing**: we can instantiate a `SwarmServerBootstrap` in a JUnit test without a
  Spring context to load, without `@SpringBootTest` to wait for.
+ **Pedagogical**: a reader opening the repo immediately sees what runs and where.
− **More plumbing code if scope grows**: externalized config, rich health endpoint,
  metrics, security — these will have to be written or added piece by piece.
− **No Spring profiles**: if we later want a `dev` vs `prod` mode, we will have to roll it
  by hand (env vars + small arg parser).
− **Re-divergence risk**: on the other Gakkel projects (fleet-dashboard, drone-embedded),
  if Spring becomes the standard, the swarm-server skeleton will stand out. Accepted.

## Alternatives considered
- **Spring Boot + `grpc-spring-boot-starter`**: rejected for the MVP. Brings useful prod
  auto-config (actuator, profiles, externalized config) with no target to serve here,
  against a cost in startup time, readability and footprint.
- **Micronaut / Quarkus**: rejected. Same benefits as Spring without the familiarity, and
  still oversized for a single-process MVP.
- **Lightweight DI framework (Guice, Dagger)**: rejected. The dependency tree fits on
  one page, a static constructor is clearer than a Guice module.
- **No bootstrap class (everything in `main`)**: rejected. `SwarmServerBootstrap` allows
  injecting an arbitrary `port` and instantiating the server in tests without duplicating
  the `main`.
