# ADR-0010: Keep the drones in a list sorted by id, and do not optimise yet

[Français](0010-sorted-drone-list.fr.md)

## Status
Proposed
Date: 2026-11-24

## Context
Issue #80 asks that two runs with the same seed give exactly the same result, bit for bit. The
fleet optimizer (#83) will run 300 to 3,000 simulations per fleet configuration and compare them,
so a hidden source of randomness would make the comparison unfair.

A test showed that the **order of the drones** changes the result. With the same drones added in
reverse order, positions differed in the last digits after 10 s
(`10.318754153179452` vs `10.31875415317946`). The reason: Boids sums the positions of the
neighbours, and floating-point additions are rounded at each step, so `(a + b) + c` is not always
equal to `a + (b + c)`. The gap then grows at each step.

Until now, `World` kept its drones in a `CopyOnWriteArrayList`, in the order they were added.
A drone eaten by the danger came back at the end of the list, so the order changed during a run.

`World` is read by several threads (ADR-0008): the simulation thread (30 Hz) and the thread that
sends the `WorldState` to Unity (20 Hz). Drones are added or removed only rarely: when the world
is created, and when a drone is lost or comes back.

## Decision
- `World` keeps its `CopyOnWriteArrayList`, but `addAgent` inserts each drone **at its place by
  id**. Every loop over the drones (Boids forces, danger, target detection) then runs in the same
  order on every run.
- Drone and danger ids are drawn from the simulation seed, so "sorted by id" is the same order
  on every run with that seed.
- **No performance work for now.** The insertion is a simple loop over the list, and the
  neighbour search still compares every pair of drones. Measured on 2026-11-24: 10 simulated
  minutes take about 0.23 s with 20 drones and 3.2 s with 100 drones. This is fast enough for
  Step 2. Optimisation waits until it is needed (#166: spatial grid, when fleets go above about
  50 drones).

## Consequences
- Same seed, same result: the test `SimulationTest.Reproducibility` checks it on every build.
- Readers still see a frozen copy of the list, as decided in ADR-0008: no lock is needed.
- Each add or remove copies the whole list and looks for the place one drone at a time. This
  costs nothing with 20 drones and rare changes, but it would matter with thousands of drones
  changing often.
- `World.agents()` still returns a `List`: no caller had to change.

## Alternatives considered
- **A sorted collection (`TreeSet`, `TreeMap`)**: sorts by itself, but is not safe when one
  thread changes it while another reads it. Rejected because Unity's thread reads the world.
- **A concurrent sorted collection (`ConcurrentSkipListSet`, `ConcurrentSkipListMap<UUID, Agent>`)**:
  sorts by itself and is thread-safe, but a reader may see the set in the middle of a change
  (not a frozen copy), it has no index access, and every caller of `World.agents()` would have
  to change. Not needed for a list that changes so rarely.
- **Sort a copy at each step (`List.copyOf(...)` then `sort`)**: simple, but sorts 30 times per
  second a list that almost never changes, and misses the other loops (danger, diagnostics) that
  read the world directly.
- **Optimise now (binary search, spatial grid)**: rejected for now. The code stays simple while
  the measured times are low. Kept for later in #166.
