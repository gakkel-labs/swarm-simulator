# ADR-0007: Keep the server frame inside the backend and send NED on the wire

[Français](0007-coordinate-frames.fr.md)

## Status
Proposed
Date: 2026-11-22

## Context
Three programs look at the same drones, and each one has its own habit for axes:

- **The backend** (`swarm-server`) was written with a Y-up frame, like a 3D engine:
  X = East, Y = Up, Z = North. Y = 0 is the water surface, and a drone underwater has a
  negative Y.
- **The marine world** uses **NED** (North, East, Down): X = North, Y = East, Z = Down.
  Depth is a positive number, which is how a depth sensor and a nautical chart give it.
- **Unity** uses a left-handed Y-up frame: X = East, Y = Up, Z = North.

For a while the docs said "the backend works in NED". This was wrong, and it made the axis
swaps hard to follow. The code now has one conversion class on each side, so it is time to
write the rule down.

## Decision
- The **gRPC contract** (`gakkel.swarm.v1`) is in **NED, in meters**. Every `Vec3` on the wire
  (positions, velocities, the target) is NED.
- The **backend domain** keeps its own frame (East, Up, North). It does not leak out.
- Each side converts in **one place only**:
  - Java: `CoordinateConversions` (`serverToNed()` / `nedToServer()`), used by
    `WorldStateBuilder` and `SimulationControlImpl`.
  - C#: `CoordinateUtils` (`NedToUnity()` / `UnityToNed()`).
- No other class swaps axes.

| | X | Y | Z | Surface |
|---|---|---|---|---|
| Backend | East | Up | North | Y = 0, underwater Y < 0 |
| Contract (NED) | North | East | Down | Z = 0, underwater Z > 0 |
| Unity | East | Up | North | Y = 0, underwater Y < 0 |

```
NED  = (server.z, server.x, -server.y)
Unity = (ned.y, -ned.z, ned.x)
```

With the default world (`SWARM_WORLD_WIDTH=100`, `SWARM_WORLD_HEIGHT=100`,
`SWARM_WORLD_DEPTH=50`), the drones move in East `[0, 100]` m, Down `[0, 100]` m and
North `[0, 50]` m. Careful: in the backend, "height" is the water column (Up/Down) and "depth"
is the North extent. These names are confusing; renaming them is listed in the tech debt.

## Consequences
+ The wire speaks the language of the marine world: real navigation data (compass, depth
  sensor, chart positions) can come in later without a new contract.
+ Unity and the backend share the same axes, so the C# conversion is the mirror of the Java one,
  and both are tested on their own.
+ A bug with axes has only two places to look.
− Two frames inside the project instead of one: a new reader must know the table above.
− The backend's "height" and "depth" names do not match their meaning until they are renamed.

## Alternatives considered
- **Move the backend domain to NED**: rejected for now. It touches every Boids rule, the
  bounds and the tests, for no gain in behaviour. It can come later, with its own ADR, if the
  backend starts reading real navigation data.
- **Send the Unity frame on the wire**: rejected. The contract would follow one viewer instead
  of the marine convention, and the control station would need yet another conversion.
- **Convert in each class that needs it**: rejected. This is how the docs and the code drifted
  apart in the first place.
