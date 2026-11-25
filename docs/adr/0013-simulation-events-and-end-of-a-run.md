# ADR-0013: Report what happens as events, and give each run an end

[Français](0013-simulation-events-and-end-of-a-run.fr.md)

## Status
Proposed
Date: 2026-11-25

## Context
The engine (`Simulation`) was built for the live demo. It wrote info logs when a drone was lost
or the target was found, brought back the drones eaten by the predator, and ran for ever. The
target only existed once the operator clicked in the viewer.

The score (#82) and the optimizer (#83) need runs of another kind: the wreck set at the start
(or no wreck), drones that stay lost, and an end. They also need to know what happened (when the
wreck was detected, which drones were lost) without reading logs. The live demo must keep working
as before (issue #172).

## Decision
1. **Domain events.** `Simulation.step()` returns the events of the step, in order:
   `TargetDetected`, `DroneLost` (cause: loss risk or demo predator), `DroneRespawned`,
   `RunEnded`. They form a sealed interface `SimulationEvent` in `domain`, each with its time of
   simulation. The engine writes no log. `SimulationLoop` logs the events in the live demo; the
   score will count them, the replay will store them.
2. **End of a run.** `EndConditions` says when a run stops:
   - `never()` for the live demo: it goes on, the operator can place a new target;
   - `missionEnd(limitTime)` for a batch run: it stops when the wreck is detected, when the limit
     time is reached, or when no drone is left. When the same step meets several reasons, the
     detection wins, unless that step went past the limit time: going over it is a failure. A
     step after the end is refused, and the live loop stops stepping a run that has ended.

   The coverage threshold will be one more reason with #133, once coverage is computed.
3. **Scenario.** A value object `Scenario` holds the wreck position (or none) and the seed. The
   wreck is placed at time 0, with no random draw, so the swarm is the same with or without it.
4. **Demo options.** `DemoOptions` groups what only the demo has: the predator and the respawn of
   the drones it eats. Batch runs use `DemoOptions.none()`: `create` refuses a mission run with
   demo options. The live demo reads them from the settings. Flocking diagnostics and the
   cohesion CSV already live in `SimulationLoop`, which batch runs do not use.

## Consequences
+ The score and the optimizer can run the engine with no demo side effect, and read the result
  from the events.
+ One place logs the live demo, and the engine stays free of logging, threads and gRPC.
+ The profiles of #177 map onto it: `demo` gives the demo options and `never()`, `batch` gives
  `none()` and `missionEnd`.
− Each caller of `Simulation.create` now passes a scenario, end conditions and demo options.
− Events are kept only for the step that made them: a caller that needs the whole run (replay)
  has to keep them itself.

## Alternatives considered
- **A listener (observer) called by the engine**: rejected for now. A returned list is simpler to
  test, has no registration order, and keeps the run fully reproducible.
- **Keep the logs and parse them for the score**: rejected. Logs are for people, and their text
  may change.
- **A provisional coverage to end on a threshold now**: rejected. Coverage needs the sonar swath
  (ADR-0011) and belongs to #133.
