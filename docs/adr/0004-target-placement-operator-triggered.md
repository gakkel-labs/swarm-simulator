# ADR-0004: Target placement is operator-triggered but architecturally pluggable

## Status
Accepted — 2026-05-19

## Context
Search-and-rescue requires placing a target the swarm must find by exploration.
We need a trigger mechanism. For the MVP, the operator clicks in Unity.
Later (v0.2), we'll want CLI-driven random placement and scenario file loading
for reproducible benchmarks and headless tests.

## Decision
- `PlaceTarget(position)` RPC as the external API (operator-initiated)
- `SimulationService.placeTarget(pos, TriggerSource)` as the internal entry point
- RPC handler is a thin adapter (validate + delegate, no logic)
- `TriggerSource` enum with OPERATOR_CLICK only for now, extensible

## Consequences
+ Adding CLI/scenario triggers later = 2 lines (new enum value + new call site)
+ Backend stays authoritative (position validated, sim clock, FoundEvent server-emitted)
+ Single code path for all triggers → consistent behavior and easier testing
− Enum with a single value looks like over-engineering at first read
  (mitigated by this ADR — future readers know why)

## Alternatives considered
- **Attraction force toward target**: rejected, turns "search" into "goto" — kills the
  emergent behavior we want to demo.
- **Unity-side detection (Unity decides when target is found)**: rejected, breaks
  backend authority and prevents headless testing.
- **Hardcoded target position in backend config**: rejected, no demo flexibility.
