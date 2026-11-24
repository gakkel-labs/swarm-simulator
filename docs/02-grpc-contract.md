# gRPC contract — SwarmObserver v1

[Français](02-grpc-contract.fr.md)

> Reference documentation for the wire protocol between the swarm server and its clients (the Unity viewer today, CLI tools such as `grpcurl`).
> Source of truth: [`contracts/src/main/proto/swarm_observer.proto`](../contracts/src/main/proto/swarm_observer.proto).

---

## 1. Overview

The server exposes three gRPC services. The main one, `SwarmObserver`, follows a **broadcast / observer** pattern:

- A client opens **one** long-lived subscription with `SubscribeWorldState`.
- The server pushes a `WorldState` snapshot at **20 Hz** for as long as the stream stays open.
- The client never sends a second request on the same stream — it only reads, or cancels.

The two other services are small unary calls: `SimulationControl.PlaceTarget` (the operator places the target, §4.4) and `PingService.SendPing` (health check). No authentication: the server runs locally and trusts everything on `localhost:50051`.

**Why server-streaming and not unary polling?**
A 20 Hz visualisation over unary calls would mean 20 round-trips per second per client (≈ 60 ms of TCP+HTTP/2 overhead per second). A single server-streaming RPC opens one HTTP/2 stream and reuses it for all frames — lower latency, lower CPU, no client-side scheduler.

---

## 2. Sequence diagram

End-to-end lifecycle of a client session:

```mermaid
sequenceDiagram
    autonumber
    participant C as Client (Unity)
    participant S as SwarmObserver (server)
    participant L as SimulationLoop (30 Hz)
    participant W as World

    L->>W: tick() calls Simulation.step(1/30 s) — apply Boids rules, update agent state (every 33 ms)
    Note over S,L: sim loop and stream broadcaster run on separate executors

    C->>S: SubscribeWorldState(SubscribeRequest{client_id="unity-1"})
    activate S
    S-->>S: register ServerCallStreamObserver in subscribers set
    S-->>C: stream opened (HTTP/2 200, no payload yet)

    loop every 50 ms (20 Hz broadcaster)
        S->>W: read agents + obstacles (snapshot)
        S-->>C: WorldState{timestamp_unix_ms, agents, obstacles}
    end

    C->>S: cancel() — client closes (Unity scene exits, error, etc.)
    S-->>S: onCancelHandler fires → remove from subscribers
    deactivate S
```

**Notes:**
- The simulation tick rate (30 Hz, `SimulationLoop.TICK_RATE_HZ`) and the broadcast rate (20 Hz, `SwarmObserverImpl.STREAM_RATE_HZ`) are intentionally **decoupled**. Clients receive the latest world state, not every physics step.
- The server holds a `Set<ServerCallStreamObserver<WorldState>>`. Each broadcast iterates that set; cancelled subscribers are removed lazily on the next tick.
- The stream **never ends** server-side under normal operation. It is the client that closes it.

---

## 3. Messages

All messages are proto3. Field numbers are stable — never renumber or repurpose, only append.

### 3.1 `Vec3`

```proto
message Vec3 {
  float x = 1;
  float y = 2;
  float z = 3;
}
```

A 3D vector in **meters**, **NED frame** (see §5.1). Used for both positions and velocities — the context is given by the parent field name (`position_xyz` vs `velocity_mps`).

`float` (32-bit) is sufficient: positions are bounded to a 100 × 100 × 50 m world, sub-millimetre precision is wasted on a screen visualisation.

### 3.2 `AgentType`

```proto
enum AgentType {
  AGENT_TYPE_UNSPECIFIED = 0;
  AGENT_TYPE_EXPLORER    = 1;
  AGENT_TYPE_SPECIALIST  = 2;
  AGENT_TYPE_CARRIER     = 3;
}
```

Role of a drone in the fleet (see the [glossary](produit/glossaire.md)). The `UNSPECIFIED = 0` member is the proto3-mandated default — receiving it indicates a server bug or a forward-compatibility gap (a future role the client doesn't know about). Clients should render unknown values as a fallback rather than crashing.

The three roles map 1:1 to the domain enum `fr.gakkel.swarmsimulator.swarmserver.domain.AgentType`: `EXPLORER` = detection drone, `SPECIALIST` = specialised drone, `CARRIER` = drone carrier. Today all drones are `EXPLORER`; the other two types are reserved and have no behaviour yet.

### 3.3 `AgentState`

```proto
message AgentState {
  string    id           = 1; // UUID
  AgentType type         = 2;
  Vec3      position_xyz = 3; // meters, NED
  Vec3      velocity_mps = 4; // m/s
}
```

Per-agent snapshot at the broadcast instant.

- `id` — RFC 4122 UUID **as string** (e.g. `"6f5a...c3d2"`). Stable for the lifetime of the agent; clients can use it as a dictionary key for stateful rendering (trails, selection, etc.).
- `type` — see §3.2.
- `position_xyz` — center of the agent, meters, NED.
- `velocity_mps` — instantaneous velocity, m/s, NED. Magnitude is bounded by `BoidsConfig.maxSpeed` (default 5.0 m/s).

No orientation field — drones are point-mass boids today. Visual heading is derived client-side from `velocity_mps`.

### 3.4 `Obstacle`

```proto
message Obstacle {
  Vec3  position_xyz = 1; // center, meters NED
  float radius_m     = 2;
}
```

A static spherical obstacle. Re-sent on every frame even though it is immutable today — it keeps the protocol simple (no separate "world geometry" RPC) and leaves the door open for dynamic obstacles later.

Obstacles have no `id`. Clients should re-key them by `(position, radius)` or just rebuild the visual list each frame.

### 3.5 `PredatorState`

```proto
message PredatorState {
  string id           = 1;
  Vec3   position_xyz = 2;
  Vec3   velocity_mps = 3;
}
```

Snapshot of the **danger** at the broadcast instant. `Predator` is the old code name of the danger (see the [glossary](produit/glossaire.md)); the message keeps its name in `v1` so the contract does not break.

- `id` — UUID of the danger, stable for the whole run. Clients should key by `id` for stateful rendering.
- `position_xyz` — danger center, meters, NED. Same coordinate frame as `AgentState.position_xyz`.
- `velocity_mps` — instantaneous velocity, m/s, NED. Its magnitude is the danger speed, set with `SWARM_PREDATOR_SPEED` (default 3.0 m/s, slower than the drones so they can escape). The catch distance is set with `SWARM_PREDATOR_EAT_RADIUS` (default 1.5 m).

No orientation field — visual heading is derived client-side from `velocity_mps`.

### 3.6 `WorldState`

```proto
message WorldState {
  int64                    timestamp_unix_ms = 1;
  repeated AgentState      agents            = 2;
  repeated Obstacle        obstacles         = 3;
  repeated PredatorState   predators         = 4;
  SearchStatus             search_status     = 5;
  float                    sensor_radius_m   = 6;
  float                    cohesion_spread_m = 7;
  Vec3                     search_area_size_m = 8;
}
```

Full snapshot of the simulated world at instant `timestamp_unix_ms`.

- `timestamp_unix_ms` — server wall clock at the moment the frame was built (`System.currentTimeMillis()`). See §5.3.
- `agents` — every agent in the world. Order is **not** guaranteed; clients must key by `id`.
- `obstacles` — every obstacle in the world. Empty list is legal.
- `predators` — every danger in the world. Contains exactly **1 entry** today. Empty list is legal (no danger). Clients must handle an empty list gracefully — hide the danger GameObject when the list becomes empty.
- `search_status` — present only when a target has been placed via `PlaceTarget`. Absent until then (proto3 default).
- `sensor_radius_m` — detection radius shared by all agents in metres. Clients use this to size the detection-zone overlay.
- `cohesion_spread_m` — mean position spread (standard deviation from centroid) smoothed over the last 30 ticks (~1 s). Zero until the first tick completes. Used by the Unity HUD and exported to `metrics/cohesion-*.csv`.
- `search_area_size_m` — size of the search area in metres, NED axes: `x` = extent to the North, `y` = to the East, `z` = down to the seabed. The area spans from `(0, 0, 0)` at the surface to this corner. All zero from an older server: clients then keep their own default size. A `PlaceTarget` outside this box is refused with `INVALID_ARGUMENT`.

Each frame is **self-contained** — no diffing, no deltas. A client that connects mid-simulation receives a complete picture on its very first frame.

### 3.7 `SubscribeRequest`

```proto
message SubscribeRequest {
  string client_id = 1; // arbitrary label for server-side logging
}
```

Sent **once** by the client to open the stream. The only field, `client_id`, is a free-form label (e.g. `"unity-editor"`, `"unity-build-win-1"`, `"cli-debug"`) used by the server to identify subscribers in logs:

```
[swarm-grpc] client 'unity-editor' subscribed — 1 total
[swarm-grpc] client 'unity-editor' disconnected — 0 remaining
```

The server does not validate, deduplicate, or enforce uniqueness on `client_id` — it is purely an operator-facing diagnostic.

### 3.8 `SearchStatus` and `TargetFoundEvent`

```proto
message SearchStatus {
  bool             target_placed   = 1;
  Vec3             target_position = 2; // NED; valid only when target_placed = true
  float            elapsed_sim_s   = 3; // seconds since placement; 0 when not placed
  TargetFoundEvent found_event     = 4; // absent until a drone detects the target
}

message TargetFoundEvent {
  string agent_id      = 1;
  float  elapsed_sim_s = 2;
}
```

The state of the search, carried by `WorldState.search_status`.

- `target_position` — where the operator placed the target, meters, NED.
- `elapsed_sim_s` — seconds since the target was placed. Today it follows the server clock.
- `found_event` — set by the first drone that comes within `sensor_radius_m` (10 m) of the
  target. It never changes after that: only the first detection counts. A new `PlaceTarget`
  replaces the target and starts the search again.

---

## 4. RPC — `SubscribeWorldState`

```proto
service SwarmObserver {
  rpc SubscribeWorldState(SubscribeRequest) returns (stream WorldState);
}
```

| Aspect             | Value                                                                    |
| ------------------ | ------------------------------------------------------------------------ |
| Cardinality        | **Server streaming** (1 request → N responses)                           |
| Input              | `SubscribeRequest` (sent once at open)                                   |
| Output             | `stream WorldState`                                                      |
| Target rate        | **20 Hz** (1 frame every 50 ms — `STREAM_RATE_HZ`)                       |
| Frame self-contained | Yes — no deltas, no diffs                                              |
| End of stream      | Client-initiated cancel; server only ends on shutdown                    |
| Backpressure       | Frames are dropped silently on per-subscriber `onNext` failure (see §4.3)|

### 4.1 Streaming semantics

The server's broadcaster runs on a dedicated `ScheduledExecutorService` (thread `swarm-broadcaster`) at a fixed period of `1000 / STREAM_RATE_HZ` ms = **50 ms**. On every tick:

1. Build a single `WorldState` from the current `World` (one allocation per tick, shared across subscribers).
2. For each subscriber, check `isCancelled()`; if so, drop it from the set.
3. Else, call `onNext(state)`. Exceptions are caught — the subscriber is dropped from the set and logged.

There is no heartbeat or keepalive frame separate from `WorldState`. The 20 Hz cadence is dense enough that gRPC's default HTTP/2 connection-level keepalive is sufficient.

### 4.2 Failure modes (client side)

| Symptom                                         | Likely cause                                            |
| ----------------------------------------------- | ------------------------------------------------------- |
| `UNAVAILABLE` on subscribe                      | Server not started, wrong port (default `:50051`)       |
| Stream stalls (no `WorldState` for > 200 ms)    | Server overloaded, GC pause, or sim loop crashed        |
| `CANCELLED` raised on the client                | Client itself cancelled the call (timeout, scene exit)  |
| `INTERNAL` raised mid-stream                    | Server-side exception during `buildWorldState`          |

Clients should treat any terminal status as **end of session** and re-issue `SubscribeWorldState` to recover — the protocol is intentionally idempotent.

### 4.3 Backpressure

The broadcaster respects gRPC flow control: before each frame it checks `isReady()` for every client. A slow client that is not ready skips that frame instead of piling frames up in server memory. This is safe because each frame holds the whole world (§3.6). If `onNext` still throws, the client is removed and logged.

### 4.4 Other RPCs — `PlaceTarget` and `SendPing`

```proto
service SimulationControl {
  rpc PlaceTarget(PlaceTargetRequest) returns (PlaceTargetResponse);
}
message PlaceTargetRequest { Vec3 position = 1; } // NED, meters
message PlaceTargetResponse {}
```

- **`PlaceTarget`** (unary, package `gakkel.swarm.v1`): the viewer sends it when the operator
  left-clicks in the scene (`TargetPlacer.cs`). The server converts the position from NED
  (`CoordinateConversions.nedToServer()`) and replaces the target. A request without `position`
  gets `INVALID_ARGUMENT`, and so does a position outside the search area (see `search_area_size_m`). See [ADR-0004](adr/0004-target-placement-operator-triggered.md).
- **`SendPing`** (unary, service `PingService`, package `gakkel`, file `ping.proto`): a health
  check. It sends back a reply to the message. It is not versioned with `v1`.

---

## 5. Conventions

### 5.1 Coordinate frame — NED (meters)

All `Vec3` values are expressed in the **NED** (North / East / Down) right-handed frame, **in meters**:

| Axis  | Direction     | Range (default world)      |
| ----- | ------------- | -------------------------- |
| `x`   | North         | `[0, 50]` m                |
| `y`   | East          | `[0, 100]` m               |
| `z`   | Down          | `[0, 100]` m               |

The backend works in another frame (East, Up, North) and converts in `CoordinateConversions`. Why, and the full table: [ADR-0007](adr/0007-coordinate-frames.md).

**Why NED:** it is the standard marine convention (depth is a positive number, like a depth sensor gives it), and it keeps the door open for real navigation data (compass, depth sensor, chart positions).

**Unity viewer note:** Unity uses a left-handed Y-up frame. Conversion happens on the client, in `CoordinateUtils.NedToUnity()`, not on the wire:

```
unity.x =  proto.y     // East  → Unity X
unity.y = -proto.z     // Down  → Unity -Y (Y-up)
unity.z =  proto.x     // North → Unity Z
```

### 5.2 Units

| Quantity     | Unit  | Proto field naming convention | Example field    |
| ------------ | ----- | ----------------------------- | ---------------- |
| Position     | meter | `_xyz`                        | `position_xyz`   |
| Velocity     | m/s   | `_mps`                        | `velocity_mps`   |
| Length       | meter | `_m`                          | `radius_m`       |
| Time         | ms    | `_ms` (or `_unix_ms`)         | `timestamp_unix_ms` |

Units are part of the **field name**, not the comment. A field without a unit suffix (e.g. `id`, `type`) carries a non-physical value.

### 5.3 Timestamp — unix ms (int64)

`WorldState.timestamp_unix_ms` is the server wall clock at frame build time, in **milliseconds since the Unix epoch (1970-01-01 UTC)**, as `int64`.

- Server source: `System.currentTimeMillis()`.
- Resolution: 1 ms — sufficient at a 20 Hz cadence (50 ms period).
- Not monotonic across NTP jumps; clients must not derive `dt` by differencing successive timestamps (use the known 50 ms cadence instead).
- `google.protobuf.Timestamp` was rejected: int64-ms is 2× smaller on the wire, easier to log, and lossless until year 292 277 026.

### 5.4 Identity — UUID as string

Agent identity is a string-encoded UUID (RFC 4122). Trade-off vs `bytes(16)`:
- **+** Human-readable in logs and gRPC reflection / `grpcurl` output.
- **−** 36 bytes vs 16 — ≈ 1.7 KiB/s overhead at 20 Hz × 20 agents. Negligible for the MVP.

If that overhead matters later (e.g. 200 drones), the field can be migrated to `bytes id_bin = 5` alongside the existing string for one release, then the string can be deprecated.

### 5.5 Naming and versioning

- **Proto package** carries the major version: `gakkel.swarm.v1`. A breaking change creates `v2`, never edits `v1` in place.
- **Java package**: `io.gakkel.swarm.contracts.v1` (`java_multiple_files = true` so each message is its own `.java`).
- **C# namespace**: `Gakkel.Swarm.Contracts.V1`.
- **Field naming**: `snake_case` in proto (proto3 convention); generators produce idiomatic `camelCase` / `PascalCase` on each language side.
- **Enum members**: prefixed with the enum name (`AGENT_TYPE_EXPLORER`, not `EXPLORER`) per the [Google API style guide](https://protobuf.dev/programming-guides/style/#enums) — avoids collisions in C++ and clarifies log output.
- **Breaking changes**: never remove or renumber a field. Mark obsolete fields with `reserved` and bump the package version when removing a message.

---

## 6. Implementation pointers

| Concern                | File                                                                                      |
| ---------------------- | ----------------------------------------------------------------------------------------- |
| Proto definition       | `contracts/src/main/proto/swarm_observer.proto`                                           |
| Server-side service    | `swarm-server/.../server/SwarmObserverImpl.java` (subscription + broadcast loop)          |
| gRPC server bootstrap  | `swarm-server/.../server/SwarmServer.java` (entry point) + `SwarmServerBootstrap.java` (wiring; port from `Settings`, default `50051`, override `SWARM_PORT`) |
| Frame source           | `swarm-server/.../simulation/SimulationLoop.java` (30 Hz Boids tick → `World`)            |
| Domain → proto mapping | `swarm-server/.../server/WorldStateBuilder.java` (`build()` / `toAgentState()` / `toProtoObstacle()` / `toPredatorState()`) |
| Coordinates server ↔ NED | `swarm-server/.../server/CoordinateConversions.java` (`serverToNed()` / `nedToServer()`), the only Java place that swaps axes |
| Coordinates NED ↔ Unity | `unity-client/Assets/Scripts/Visualization/CoordinateUtils.cs` (`NedToUnity()` / `UnityToNed()`), the only C# place that swaps axes |

To exercise the stream manually:

```bash
# requires grpcurl + reflection enabled (already wired in SwarmServer)
grpcurl -plaintext -d '{"client_id":"grpcurl"}' \
  localhost:50051 \
  gakkel.swarm.v1.SwarmObserver/SubscribeWorldState
```

---

## 7. Roadmap impact

The contract is intentionally minimal so future iterations slot in without breaking v1 clients:

| Future need                           | Likely extension                                                                |
| ------------------------------------- | ------------------------------------------------------------------------------- |
| Danger                                | ✅ **Done** — `repeated PredatorState predators = 4` on `WorldState`. The danger chases the drones; Unity draws it as a shark (detailed view) or a red sphere (simple view), see `PredatorRenderer.cs`. |
| Drone carrier and specialised drones  | Existing `AgentType` enum already covers `CARRIER` / `SPECIALIST`               |
| Commands to the fleet (Step 6)        | **New** service, separate RPC — keep the observer read-only                     |
| Fleet configurations and scores (Step 2–3) | New messages or a new service; the observer stays as it is                 |
| Replay (Step 3)                       | Same `WorldState` frames, read from a file instead of the live loop             |
| Per-agent telemetry (battery, health) | New `optional` fields on `AgentState` (proto3 `optional` is wire-compatible)    |

When in doubt: **add fields, never repurpose them**. Bump the package to `v2` only for true breaking changes.
