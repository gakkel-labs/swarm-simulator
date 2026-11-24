# ADR-0011: Use a realistic scale, a drone specification and realistic sensors for scoring

[Français](0011-model-assumptions-and-scale.fr.md)

## Status
Proposed
Date: 2026-11-24

## Context
Step 2 scores fleet configurations by their mission success probability (#82). Before we score
anything, the simulation must model a problem that exists at sea. Today it does not:

| | Today | At sea (civil wreck search) |
|---|---|---|
| Search area | world of 100 x 100 x 50 m (0.01 km²) | 50 to 100 km² (glossary) |
| Sensor | a 10 m sphere, in open water, "seen or not seen" | a side-scan sonar: a band of seabed on each side, a blind gap under the drone, a detection chance that falls with range, false alarms |
| Detection drone motion | free 3D flocking, can move sideways | dives, follows the seabed at a fixed altitude, turns with a limited turn rate, comes back up |
| Speed | 5 m/s (about 10 knots) | about 1.5 m/s (3 knots) |
| Neighbours (Boids) | each drone knows the exact position and speed of every neighbour | no GPS underwater; drones hear positions sent by an acoustic modem, late, short range, some messages lost |
| Obstacles | known everywhere | seen only by a forward sonar, about 100 m ahead |
| Energy | none | a fixed endurance |
| Position on the chart | none (local metres only) | latitude / longitude, needed by the nautical chart of Step 3 |
| Simulation step | 1/30 s, real time | a batch needs to run hours of mission in seconds |

The Unity live demo must keep working: it shows a small world, in real time, and it is part of
how the project is shown.

## Decision

### 1. Two profiles, one engine
The engine and its rules are the same. Only the values change, read from the configuration.

| | `demo` (Unity, live) | `batch` (score #82, optimiser #83) |
|---|---|---|
| Search area | 100 x 100 m, as today | 7 x 7 km (about 50 km²) |
| Simulation step | 1/30 s, real time | 1 s, as fast as possible |
| Seabed | flat, as today | flat at 1,000 m by default |
| Unity | yes | no |
| Drone values | today's values (they look good on screen) | the drone specification below |

The demo is a show, not a measurement: its numbers are never used for a score.

Why 1 s in batch: at 1.5 m/s a drone moves 1.5 m per step, much less than the sonar band, so a
wreck cannot be "jumped over". 16 hours of mission are 57,600 steps instead of 1.7 million.
Results depend on the step, so the step is part of the batch profile and never changes between
two configurations that are compared.

### 2. A drone specification for each drone type
A fleet configuration says "which drone type, how many". Each drone type has a **drone
specification**: its technical values. It is a value object (a Java `record`, no id, never
changes). Every part of the engine reads only what it needs from it. When the drone carrier and
the specialised drones come (#35), they get their own specification without changing the engine.

Detection drone, `batch` values (order of magnitude of small search drones rated for deep water):

| Group | Value | Step 2 |
|---|---|---|
| Motion | cruise speed | 1.5 m/s (about 3 knots) |
| | max turn rate | 10°/s |
| | dive and ascent speed | 0.5 m/s |
| | working altitude above the seabed | 15 m |
| | max depth | 6,000 m (the Gakkel Ridge demo seabed is about 3,900 m deep) |
| Energy | endurance | 16 h, so a range of about 86 km |
| Search sensor | side-scan sonar | 50 m on each side, blind gap under the drone ≈ altitude (15 m) |
| | detection chance | 0.9 from 15 to 35 m, then falls in a straight line to 0 at 50 m |
| | false contacts | about 0.5 per km² of seabed swept |
| Navigation sensors | pressure sensor | depth of the drone |
| | altimeter / Doppler velocity log | distance to the seabed (up to 200 m) |
| | forward obstacle sonar | a cone of 100 m, 120° wide, in front of the drone |
| Acoustic modem | range | 1 km |
| | period | one position message every 10 s |
| | lost messages | 15% |

### 3. Detection drones move in 3D, with two separate decisions
- The **horizontal plane** (North, East) is decided by the search pattern: lawnmower lines, Boids
  neighbours.
- The **vertical** is decided by **seabed following**: target depth = seabed depth under the
  drone − working altitude. If the seabed rises, the drone rises.

Why separate them: the side-scan sonar only works well at its working altitude. If Boids also
decided the vertical, two drones could stack one above the other and search the same band of
seabed. Real search drones do the same: a route pilot in the horizontal plane, and a separate
altitude loop.

A detection drone goes through three phases:
1. **Dive** from the drop point to the working altitude.
2. **Search**: it follows the seabed. The sonar only counts in this phase.
3. **Ascent** at the end of its endurance or of the mission.

Dive and ascent take time and endurance, so the score takes them into account.

The seabed is a function "seabed depth at (North, East)". It is flat by default in Step 2. A slope
or real relief can be plugged in later without touching the rest (see
[seabed bathymetry](../04-seabed-bathymetry.md)).

### 4. Two families of sensors
- **Search sensors** see a band of seabed and create **contacts**. Almost all of them have the same
  shape: a range on each side, an optional blind gap under the drone, a detection curve, a working
  altitude. Only the numbers change. Step 2 codes the side-scan sonar only. Later missions add
  other search sensors with the same shape: synthetic aperture sonar and magnetometer (mine
  warfare), multibeam echosounder (mapping), camera (Confirm).
- **Navigation sensors** only help the drone move without hitting anything: depth, altitude,
  forward obstacle sonar. A drone sees an obstacle only inside its forward cone. It climbs over it
  if it can, else it turns.

The detection chance is drawn from the scenario seed: the same scenario still gives the same
result (ADR-0010).

**False contacts** (a rock that looks like a wreck) are counted, but they do not change the score
in Step 2: Confirm does not exist yet (#82 leaves it out).

**Coverage** counts the seabed that went through the useful band of the sonar (between the blind
gap and the max range) at least once. The detection chance is used for the wreck only.

### 5. Boids neighbours come from the acoustic modem
Drones do not see each other with their sonars: they **listen** to each other. Every 10 s each
drone sends its position and speed. A drone only knows the neighbours in modem range, with the
last position it heard (up to 10 s old, so about 15 m off at 1.5 m/s). When a message is lost, it
keeps the last known position. Boids (separation, alignment, cohesion) runs on these announced
positions. The forward obstacle sonar is the safety net if a neighbour is still too close in front.

This keeps Boids at the heart of the project, and makes it more interesting to show: a swarm that
stays together with late and incomplete information is exactly the real problem.

### 6. Lawnmower line spacing comes from the sensor
Lawnmower lines (#156) are spaced by about one side's range (50 m), not by twice the range: then
the blind gap under one line is seen by the next line. The spacing is a setting of the lawnmower,
so the optimiser can try tighter lines (better detection chance, more time).

Order of magnitude: one drone sweeps 50 m x 1.5 m/s ≈ 0.27 km² per hour, so 50 km² take about
185 drone-hours, about 19 hours with 10 drones. This is more than 16 h of endurance: choosing
how many drones and which spacing is exactly the trade-off the optimiser (#83) must show.

### 7. The search area is tied to a real position
The search area has an **origin**: the latitude / longitude (WGS84) of its south-west corner.
Positions stay in local metres, in the frames of ADR-0007. The conversion uses a flat-earth
approximation:

```
north_m = (latitude  − origin_latitude)  × 111,320
east_m  = (longitude − origin_longitude) × 111,320 × cos(origin_latitude)
```

On 10 km the error is well below a sonar band, even at the latitude of the Gakkel Ridge.

### 8. What is simplified or left out, and why

| Topic | Step 2 | Why |
|---|---|---|
| Own position (navigation drift without GPS) | perfect | the error grows slowly over a few hours; it matters for Confirm, later |
| Current | none | not needed to compare configurations with each other |
| Seabed relief | flat by default, function ready | relief changes the sonar shadows, a later step |
| Energy | fixed endurance in hours | a battery model would change little for the score |
| Seabed type, sonar shadows, sea state | none | second-order effects |
| Drone carrier and specialised drones | none | no such drones in Step 2 (#35) |
| Communication with the drone carrier | none | the swarm talks to itself only |

## Consequences
+ The score (#82) measures a problem that exists at sea: the right size, speed, sensor and time.
+ The Unity live demo keeps working with no change: it is the `demo` profile.
+ New drone types and new sensors are new specifications, not new engine code.
+ Boids stays at the heart of the swarm, with realistic limits.
− More values to explain and to keep up to date (the drone specification). They are orders of
  magnitude, not the figures of one real drone.
− Batch runs are longer (hours of mission per scenario). To be measured in #83.
− Several follow-up issues before the score can run at this scale.

Follow-up issues: drone specification as code, demo and batch profiles, 3D motion with seabed
following, side-scan sonar, Boids neighbours from the acoustic modem, forward obstacle sonar,
search area origin in WGS84.

## Alternatives considered
- **Keep the small world and scale the results up**: rejected. Swarm behaviour does not scale in a
  straight line (neighbours, boundaries, turns), so the scaled score would not mean anything.
- **One profile at real scale for everything, Unity included**: rejected. A 7 km world with 1.5 m/s
  drones shows nothing on screen in real time, and the demo is how the project is shown.
- **Free 3D motion decided by Boids**: rejected. Drones would stack vertically and the sonar would
  work outside its altitude.
- **Perfect neighbour knowledge kept for Boids**: rejected. It does not exist underwater, and it
  would make Boids look better than it can be at sea.
- **A detailed acoustic model (sound speed, noise, multipath)**: rejected for now. A range, a period
  and a loss rate are enough to compare configurations.
