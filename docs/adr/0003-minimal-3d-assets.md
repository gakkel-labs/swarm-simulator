# ADR-0003: Minimal 3D assets — grey capsules, primitive shapes, no art pipeline

## Status
Accepted — 2026-01-15 (retroactive, formalized 2026-05-25)

## Context
The Unity client displays an AUV swarm, a SAR target, a predator and obstacles in a 3D scene.
The aesthetic question arises as soon as Unity opens: should we model real AUVs (textured
submarines, water FX, post-processing, marine skybox) or stick to primitives?

Forces at play:
- **The subject of the project is behavioral emergence**, not underwater rendering. The
  demonstrable value is swarm cohesion, predator avoidance, SAR search pattern — not the
  quality of a shader.
- **The backend is the authority** (see ADR-0001/ADR-0004). The client is disposable,
  replaceable by a 2D dashboard tomorrow without breaking anything. Investing in costly
  Unity assets would contradict this boundary.
- **Solo personal project**, no game designer, no 3D artist. Every hour spent texturing
  is an hour not spent on Boids, the Pi agent (v0.3) or ACO (v0.5).
- **Demo runnable in 2 minutes** (CLAUDE.md). A Unity project with heavy assets increases
  Play time, repo weight, and install friction.
- **Behavior readability**: with 50 identical grey capsules, the eye immediately identifies
  group dynamics. With 50 detailed unique models, the visual pattern blurs.
- **Gakkel consistency**: sister projects (`gakkel-fleet-dashboard`, `gakkel-drone-embedded`)
  aim for operational readability, not photorealism.

## Decision
- **Agents**: Unity primitive `Capsule` `GameObject`, uniform grey material (Lit URP, single
  color). No texture, no animation, no per-`AgentType` variation beyond a possible tint later.
- **SAR target**: primitive `Sphere`, material switches to green when the target is found
  (`SearchStatus.found_event`).
- **Predator**: distinct primitive (contrasting shape and color) — an easily identifiable
  volume, not a realistic threat model.
- **Obstacles**: geometric primitives (`Cube` / `Cylinder`), neutral material.
- **Environment**: default Unity skybox, no water, no volumetric post-processing, no FX.
  Standard URP, no custom pipeline.
- **Camera**: basic `CameraController.cs` (orbit / pan / zoom), no cinematic.
- **HUD**: plain text `SimulationUI.cs` (counters, timer). No motion design.

## Consequences
+ **Visual focus on emergence**: an observer immediately sees cohesion, alignment,
  separation, and predator flight. The eye is not distracted.
+ **Fast iteration**: we can test a Boids change without going through Unity Editor to
  re-import an FBX or re-bake a lightmap.
+ **Light repo**: no heavy binaries, no pressure on Git LFS, fast cloning.
+ **Reproducible demo anywhere**: standard URP runs on any recent machine without a
  dedicated GPU.
+ **Zero replacement cost**: if one day we want a real AUV (post-v0.6 or a client demo),
  swapping the `Agent` prefab mesh is enough — no script depends on the shape.
− **Demo not visually "impressive" at first sight**: a casual viewer expecting AAA
  underwater visuals will be disappointed. The pitch must own the bias ("focus on behavior").
− **If the project pivots toward a consumer product or a client deliverable**, the visual
  investment will have to be done at that point — not before.
− **Per-type visual identification (v0.4)**: at least 3 colors will be needed to distinguish
  EXPLORER/OPERATOR/CARRIER. Trivial, not blocking.

## Alternatives considered
- **Realistic AUV models (textured FBX + animation)**: rejected. High hourly cost, zero
  pedagogical value, obscures the reading of emergence.
- **Underwater asset store pack**: rejected. External dependency, license to manage, often
  generic visuals, and still a cost in Play time and weight.
- **Water shader + volumetric post-processing**: rejected. Pretty but costs FPS and hides
  agents at depth. Out of MVP scope.
- **Distinct per-agent colors from day one**: rejected for v0.1. We do not yet differentiate
  types (`AgentType` exists in the proto but is not visually exploited before v0.4).
  Keeping uniform grey reinforces the "swarm" reading.
- **2D top-down rendering**: rejected. The simulation is 3D (the vertical axis is useful
  for the depth search pattern), 2D rendering would lose that dimension.
