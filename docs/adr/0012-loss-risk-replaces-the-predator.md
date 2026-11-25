# ADR-0012: Lose drones through a loss risk, and keep the predator for the demo only

[Français](0012-loss-risk-replaces-the-predator.fr.md)

## Status
Proposed
Date: 2026-11-25

## Context
Since Step 1, the swarm flees a **predator** that swims to the nearest drone and "eats" it.
The glossary called it a "danger" (a ship, a net, an animal, a no-go area). Issue #130 first
planned to rename it and add danger levels (number of predators, speed, catch radius).

Before coding, we looked at published studies of underwater drone losses:

- Drones are almost never lost to something that chases them. They are lost through ordinary
  faults (leak, battery, navigation, implosion at depth), made worse where nobody can recover
  them (ice, great depth, bad weather) ([Brito, Griffiths and Challenor, 2010](https://nora.nerc.ac.uk/id/eprint/169592/)).
- The risk grows with the time or distance spent diving. The same fault is about 5 times more
  likely to lose the drone under sea ice, and 9 times under an ice shelf, than in open water.
- In coastal waters, two REMUS-100 drones made 189 missions without a loss; experts estimate
  0.3 % to 1.9 % per 14 km mission ([Griffiths et al., 2009](https://nora.nerc.ac.uk/id/eprint/169184/)).
- A polar study gives 0.18 % per 24 h mission from internal faults alone, and 4.4 %, 13.2 %
  and 35 % in good, moderate and bad polar conditions ([JMSE, 2023](https://www.mdpi.com/2077-1312/11/10/1976)).

A predator with a speed and a catch radius does not model this. But it looks good in the live
view, and it explains why a drone disappears.

## Decision
1. **Loss risk** (`LossRiskLevel`) is the mission concept. The operator picks a level for the
   search area. Each level gives the chance of losing one drone in 24 h of diving:

   | Level | Loss in 24 h of diving | Typical situation |
   |---|---|---|
   | `NONE` | 0 % | ideal conditions, live demo |
   | `LOW` | 0.2 % | internal faults only, mature drone |
   | `MEDIUM` | 2 % | coastal waters |
   | `HIGH` | 5 % | rocky coasts, dense traffic; about good polar conditions |
   | `EXTREME` | 15 % | about moderate polar conditions |

   The bad polar value (35 %) is left out: a civil wreck search would not be launched at that
   risk.
2. The rate is constant: `p(step) = 1 - exp(-rate x dt)`, with the rate taken from the 24 h value.
   At each step, the simulation draws from the seed, drone by drone in id order, whether the drone
   is lost. A lost drone leaves the world, never comes back, and is listed in `lostDroneIds()`.
   With `NONE` there is no draw at all, so runs are exactly the same as before.
3. The **predator** stays, for the **live demo only**. It is on by default in the live view
   (`SWARM_PREDATOR_ENABLED=true`), with loss risk `NONE`, so no drone disappears without a
   visible reason. `Simulation.create` without a predator gives none: batch runs never have one.
4. "Danger zone" (a place where the loss risk is higher) is not added now. It is studied in #190.

## Consequences
- The optimiser (#83) and the mission file (#161) can use one simple input: the loss risk level.
- Lost drones become a result of a run, as decided for #83 (lost drones stay lost).
- The values are orders of magnitude, mostly from polar studies and expert judgment on one
  drone. They live in one place (`LossRiskLevel`) and can be changed.
- One level for the whole mission is a simplification: real risk depends on the mission phase
  (launch and recovery are the riskiest, see #178), the depth and the weather.
- The demo predator still uses the Boids flee rule, so Boids stays visible in the live view.

## Alternatives considered
- **Rename the predator to "danger" and scale it by level** (number, speed, catch radius): not
  realistic, and one word would mean two things (an animal and a risk).
- **A loss probability per mission, drawn once**: simpler, but it ignores how long each drone
  dives, and the search patterns do not all dive for the same time.
- **Use the 35 % bad polar value for `EXTREME`**: too high for a civil search; operators accept
  10 % to 23 % for a whole polar campaign.
