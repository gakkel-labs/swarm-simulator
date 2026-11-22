# ADR-0008: Share the World between threads without locks

[Français](0008-shared-world-concurrency.fr.md)

## Status
Proposed
Date: 2026-11-22

## Context
Three kinds of threads touch the same `World` (see [architecture](../architecture.md)):

- **`sim-loop`** (30 Hz) moves the drones and the danger. It also removes a drone when the
  danger catches it, and adds it back 5 s later.
- **`swarm-broadcaster`** (20 Hz) reads the whole world to build a `WorldState`.
- **gRPC threads** place the target (`PlaceTarget`).

Before #79, the drone and obstacle lists were plain `ArrayList`s. The broadcaster could read the
list while the simulation removed a drone: this can throw a `ConcurrentModificationException`
or show a half-changed list. The docs called these lists "immutable", which was not true.

We want a fix that stays simple to read (this is a learning project), and that is fast enough
for a few dozen drones at 30 Hz.

## Decision
No `synchronized` and no lock. Each piece of shared state uses the lightest tool that makes it safe:

| State | Tool | Why |
|---|---|---|
| `World.agents`, `World.obstacles` | `CopyOnWriteArrayList` | Many reads (every tick, every broadcast), rare writes (a drone caught or respawned). A reader always walks a stable copy. |
| `World.predator`, `World.target` | `volatile` field | Replaced as a whole; readers see the newest reference. |
| `Agent` / `Predator` position and velocity | `volatile Vector3D` (immutable record) | A reader never sees a half-written vector. |
| `Target` found record | `AtomicReference` | Only the first drone that detects the target wins (compare-and-set). |
| `CohesionMetric`, `FlockingDiagnostician` results | `volatile` field | Written by `sim-loop`, read by the broadcaster. |

Inside one tick, `SimulationLoop` first takes a copy of the drone list (`List.copyOf`) and
computes all forces from that copy, then moves the drones.

What this gives:
- **Per-item consistency**: each drone is either in the list or not, and each vector is
  whole.
- **No snapshot consistency**: one `WorldState` can mix drones from tick N and tick N+1, and a
  drone's position and velocity can come from two ticks next to each other. At 20 Hz on screen
  this cannot be seen.

## Consequences
+ No crash when drones come and go during a broadcast.
+ Easy to read: no lock order, no risk of deadlock.
+ Light at our size: a copy of 20 references when a drone is caught.
− A `WorldState` is not one exact moment. Anything that needs an exact moment (a replay frame,
  a score) must not rely on it.
− Copy-on-write gets slow if the list changes often (for example hundreds of drones dropped
  one by one each tick).

This decision must be **reviewed at Step 2**. The optimizer needs repeatable runs with a
simulation clock (`step(dt)`), and then a clean state at each tick. The likely next step: the
loop builds an immutable snapshot of the world at the end of each tick and publishes it with
one `volatile` (or `AtomicReference`) write; readers only use that snapshot.

## Alternatives considered
- **`synchronized` or a `ReadWriteLock` around the world**: rejected for now. It gives snapshot
  consistency, but the broadcaster would block the simulation (or the other way round), and
  lock order becomes one more thing to get right.
- **An immutable snapshot published at each tick**: the best target, but it changes how
  `SimulationLoop` and `World` are written. Kept for Step 2.
- **Keep `ArrayList` and catch the exception**: rejected. It hides the race instead of removing it.
