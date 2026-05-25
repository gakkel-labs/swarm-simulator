# ADR-0001: Use gRPC server-streaming to push WorldState at 20 Hz

## Status
Accepted — 2026-01-15 (retroactive, formalized 2026-05-25)

## Context
The Unity client must display in real time the state of a simulated AUV swarm running on the
Java backend: positions and velocities of ~50 agents, plus the target, predator and obstacles.
The backend is the authority (see ADR-0004), Unity is only a renderer.

Forces at play:
- **Continuous high-frequency push**: the simulation runs at 30 Hz on the server, we want
  to feed Unity at a rate sufficient for smooth motion (≥ 20 Hz), without saturating the
  local network.
- **Low latency**: a swarm that "jumps" every 200 ms breaks the emergence perception.
- **Shared typed schema**: the contract must be compile-time verified to limit
  Java ↔ C# desynchronization bugs.
- **Multi-language backend over time**: the Unity client is C#, the Pi agent (v0.3) will be
  Java, a future dashboard could be TypeScript. We want one shared contract everywhere.
- **Personal project, frugal MVP**: no broker (NATS, Kafka) and no service mesh.

## Decision
- Single transport: **gRPC** over HTTP/2 cleartext (h2c) on localhost.
- Pattern: **server-streaming** unidirectional to push `WorldState` at **20 Hz**
  (`SwarmObserver.SubscribeWorldState`).
- Complementary pattern: **unary RPCs** for occasional operator commands
  (`SimulationControl.PlaceTarget`, `PingService.SendPing`).
- Serialization: **Protocol Buffers proto3**, contracts centralized in the Maven module
  `contracts/`, which auto-generates Java stubs (`protobuf-maven-plugin`).
  C# stubs are generated via `Grpc.Tools` in `unity-client/`.
- Tick / broadcast decoupling: the simulation loop runs at **30 Hz**, the broadcaster at
  **20 Hz** on a separate `ScheduledExecutorService`. The client does not dictate the rate.

## Consequences
+ Strongly-typed contract shared across Java/C# (and soon Java/Pi) with auto-generation.
+ Native HTTP/2 streaming: a single long-lived connection, no polling, ~ms latency on localhost.
+ Backpressure handled by gRPC (`ServerCallStreamObserver.isReady()`) — a slow client does
  not block the broadcaster.
+ Healthcheck (`Ping`) and reflection (`ProtoReflectionServiceV1`) come for free
  → we can introspect the server from `grpcurl` with zero effort.
+ Multi-client friendly: the broadcaster pushes to N subscribers via a `ConcurrentHashMap`,
  opening the door to multiple simultaneous views (Unity + dashboard).
− Heavy dependency: `grpc-netty-shaded` adds ~10 MB to the fat jar (acceptable outside
  embedded targets).
− On Unity, gRPC.Core is deprecated; we use `grpc-dotnet` + `YetAnotherHttpHandler` for
  HTTP/2 — a bit of Unity-specific plumbing.
− Non-trivial Unity threading: the stream arrives on a .NET thread, a dispatcher to the
  main thread is required (see [notes-threading-grpc-unity.md](../notes-threading-grpc-unity.md)).

## Alternatives considered
- **REST + JSON polling**: rejected. At 20 Hz, 50 polls/s with HTTP/1.1 overhead and JSON
  parsing, no typed schema, variable latency. Does not scale to multiple clients.
- **WebSocket + ad-hoc JSON**: rejected. Untyped schema, no stub generation, we would
  reinvent serialization and contract versioning.
- **MQTT / NATS / Kafka**: rejected. A broker to deploy and operate for a single-machine
  personal project. Oversized for a frugal MVP (see [ADR-0002](0002-no-spring-boot-for-mvp.md)).
- **Raw UDP**: rejected. No schema, no reliability, we would rebuild a protocol.
  No perceptible latency gain on localhost.
- **gRPC bidirectional streaming**: rejected for the MVP. Operator commands are rare
  (`PlaceTarget`); separate unary RPCs are easier to reason about.
