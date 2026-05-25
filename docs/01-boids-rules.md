# Boids — Reynolds' 3 rules (+ boundary repulsion)

> Documentation of the algorithmic core of the simulation.
> Reference: **Reynolds, C. W. (1986)** — *"Flocks, herds, and schools: A distributed behavioral model"*, ACM SIGGRAPH '87 Conference Proceedings, pp. 25–34.

---

## 1. Overview

Each agent (**boid**) applies, every tick, **Reynolds' 3 rules** (separation, alignment, cohesion) plus an **environmental rule** added outside the pure model (`boundaryRepulsion`) to handle world boundaries. Each rule produces a **unit direction vector**. These vectors are weighted, summed, then applied to velocity.

**Key principle:** an agent only perceives its **local neighbors** within a single perception radius (`perceptionRadius`). No global knowledge, no central coordination. Emergent flocking comes from the composition of these simple rules.

> **Scoping note:** §3 to §5 describe the 3 Reynolds rules. §5.4 describes `boundaryRepulsion`, which is not a Reynolds rule but a practical necessity (see §11.5). §6 shows how all 4 are combined.

---

## 2. Schema of the rules

```mermaid
flowchart LR
    A[Agent i] --> P{Perceive neighbors<br/>within perceptionRadius}
    P --> S[Rule 1: Separation<br/>avoid collisions]
    P --> AL[Rule 2: Alignment<br/>match velocity]
    P --> C[Rule 3: Cohesion<br/>stay grouped]
    P --> B[Rule 4: BoundaryRepulsion<br/>avoid world edges]
    P --> F[Rule 5: PredatorFlee<br/>escape predator]
    S -->|× w_sep| SUM((Σ))
    AL -->|× w_align| SUM
    C -->|× w_coh| SUM
    B -->|× w_boundary| SUM
    F -->|× w_flee| SUM
    SUM --> V[Update velocity<br/>v = clamp(v + steer·dt, MAX_SPEED)]
    V --> POS[Update position<br/>p += v · dt]
```

ASCII version for terminals:

```
                    ┌─────────────────────┐
                    │   Neighbors of i    │
                    │  (perceptionRadius) │
                    └──────────┬──────────┘
                               │
         ┌──────────────┬──────┴───────┬──────────────┬─────────────┐
         ▼              ▼              ▼              ▼             ▼
      SEPARATION    ALIGNMENT       COHESION       BOUNDARY     PREDATOR
     (push away)  (match heading)  (centroid)    (avoid edges)   FLEE
         │              │              │              │             │
       × w_sep      × w_align       × w_coh      × w_boundary  × w_flee
         └──────────────┴──────┬───────┴──────────────┴─────────────┘
                               ▼
                          Σ steering
                               │
                               ▼
                 v = clamp(v + steer·dt, MAX_SPEED)
                          p += v · dt
```

---

## 3. Rule 1 — Separation

**Goal:** avoid colliding with nearby neighbors.

**Principle:** for each perceived neighbor, generate a repulsion vector weighted by **1/d²** — the closer the neighbor, the stronger the force. Return the normalized direction of the cumulative repulsion.

```text
function separation(agent, neighbors):
    sum = (0, 0, 0)
    for each n in neighbors:                  # all neighbors within perceptionRadius
        away = agent.position - n.position
        d = away.magnitude()
        if d > ε:
            sum += away / (d * d)             # weighted by 1/d² → closer = much stronger
    if sum.magnitude() < ε:
        return (0, 0, 0)                      # no neighbors, or exactly overlapping ones
    return normalize(sum)                     # unit direction vector
```

> **Note:** all neighbors are processed, not just those within a separation sub-radius. Quadratic decay makes the repulsion **negligible** at the edge of `perceptionRadius` (≈ 1/225 at d=15 vs ≈ 1 at d=1), which makes a dedicated `separationRadius` unnecessary in practice. The strict cutoff is still enforced by the perception layer, which pre-filters neighbors.

---

## 4. Rule 2 — Alignment

**Goal:** match heading (and speed direction) with perceived neighbors.

**Principle:** sum neighbors' velocity vectors, return the normalized direction of that sum.

```text
function alignment(neighbors):
    if neighbors is empty:
        return (0, 0, 0)
    sum = (0, 0, 0)
    for each n in neighbors:
        sum += n.velocity
    if sum.magnitude() < ε:
        return (0, 0, 0)                      # velocities cancel each other out
    return normalize(sum)                     # direction of collective velocity
```

---

## 5. Rule 3 — Cohesion

**Goal:** stay grouped with the flock.

**Principle:** compute the **centroid** (center of mass) of neighbors, return the normalized direction toward that point.

```text
function cohesion(agent, neighbors):
    if neighbors is empty:
        return (0, 0, 0)
    sum = (0, 0, 0)
    for each n in neighbors:
        sum += n.position
    centroid = sum / count(neighbors)
    diff = centroid - agent.position
    if diff.magnitude() < ε:
        return (0, 0, 0)                      # agent already at the centroid
    return normalize(diff)                    # direction toward the group's center
```

---

## 5.4 Rule 4 — BoundaryRepulsion *(outside the Reynolds model)*

**Goal:** prevent agents from leaving the simulated world. **This rule is not part of the Reynolds 1987 model** — it's a necessary practical addition (see §8 "No boundaries").

**Principle:** for each world axis, if the agent is closer than `boundaryRepulsionRadius` to a wall, generate a repulsive force proportional to proximity (1 at the wall, 0 at the radius boundary). Return the normalized direction.

```text
function boundaryRepulsion(agent, world):
    push = (0, 0, 0)
    for each axis in (x, y, z):
        dist_min = agent.position[axis] - world.min[axis]
        dist_max = world.max[axis] - agent.position[axis]
        if dist_min < boundaryRepulsionRadius:
            push[axis] += 1 - dist_min / boundaryRepulsionRadius
        if dist_max < boundaryRepulsionRadius:
            push[axis] -= 1 - dist_max / boundaryRepulsionRadius
    if push.magnitude() < ε:
        return (0, 0, 0)                      # agent far from all boundaries
    return normalize(push)
```

---

## 5.5 Rule 5 — PredatorFlee *(threat response, outside the Reynolds model)*

**Goal:** repel each agent away from the autonomous predator when it enters `threatFleeRadius`. The predator is a **single moving threat**, not a flock neighbor — it is handled separately from the `perceptionRadius` neighbor list.

**Principle:** inverse-square repulsion (same kernel as Separation). The rule is **not normalized** — raw magnitude grows as the predator closes in, giving a reactive emergency flee rather than a constant drift.

```text
function predatorFlee(agent, predator):
    away = agent.position - predator.position
    d = away.magnitude()
    if d > threatFleeRadius or d < ε:
        return (0, 0, 0)              # predator out of range, or co-located (degenerate)
    return away / (d * d)             # NOT normalized — magnitude encodes urgency
```

> **Difference from Separation:** `separation` is normalized (always unit magnitude); `predatorFlee` is not. Combined with `threatFleeWeight = 200.0`, the flee force overwhelms all flocking forces within a few meters — panic overrides formation. Outside `threatFleeRadius` the force is exactly zero.

---

## 6. Composition

The total steering vector applied to the agent every tick:

```text
steer = w_sep      * separation(agent, neighbors)
      + w_align    * alignment(neighbors)
      + w_coh      * cohesion(agent, neighbors)
      + w_boundary * boundaryRepulsion(agent, world)
      + w_flee     * predatorFlee(agent, predator)    # only if predator != null and in range

agent.velocity += steer * dt
agent.velocity  = clamp(agent.velocity, MAX_SPEED)   # only cap applied
agent.position += agent.velocity * dt
```

> **Note:** rules 1–4 return unit vectors (magnitude = 1). `predatorFlee` (rule 5) returns an un-normalized vector — its magnitude grows as 1/d² inside `threatFleeRadius`. There is no `clamp(steer, MAX_FORCE)` — the total magnitude of `steer` is practically bounded by the sum of weights plus the flee term, which decays to zero outside the flee radius.

---

## 7. Coefficients and their effect

Default values (`BoidsConfig` class):

| Parameter                  | Default value     | Role                                                            | Effect if increased                          |
| -------------------------- | ----------------- | --------------------------------------------------------------- | -------------------------------------------- |
| `perceptionRadius`         | `15.0`            | Single perception radius for all rules                          | Larger neighborhood, more global behavior    |
| `separationWeight`         | `1.5`             | Weight of separation in the final sum                           | Looser flock, larger spacing                 |
| `alignmentWeight`          | `1.0`             | Weight of alignment                                             | More parallel motion, "school" effect        |
| `cohesionWeight`           | `1.0`             | Weight of cohesion                                              | Tighter, more compact flock                  |
| `maxSpeed`                 | `5.0`             | Maximum agent speed                                             | Faster, more reactive flock                  |
| `boundaryRepulsionRadius`  | `15.0`            | Distance from walls below which repulsion kicks in              | Larger safety margin at edges                |
| `boundaryRepulsionWeight`  | `2.0`             | Weight of boundary repulsion                                    | Agents bounce off walls earlier/harder       |
| `threatFleeRadius`         | `15.0`            | Predator detection radius — flee activates within this distance | Wider panic zone                             |
| `threatFleeWeight`         | `200.0`           | Weight of predatorFlee in the final sum                         | Stronger/weaker panic response               |
| `dt` *(simulation loop)*   | `1/30 s ≈ 33 ms`  | Integration timestep (30 Hz)                                    | Larger steps → numerical instability         |
| `ε` *(numerical guard)*    | `1e-6`            | Threshold below which a vector is considered zero               | Too large → rules ineffective near zero      |

**Rules of thumb:**
- `w_sep` slightly higher than `w_align` and `w_coh` → avoids collisions while preserving flocking.
- `boundaryRepulsionWeight` > `cohesionWeight` → edges take priority over staying grouped.
- Increasing `perceptionRadius` without retuning weights makes behavior more "global" — the flock acts as one block.

---

## 8. Known limitations of the pure model

| Limitation                | Description                                                                                                | Status in this project / mitigation                              |
| ------------------------- | ---------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------- |
| **Overshooting**          | An agent overshoots its target (centroid) and oscillates around it. The `desired − velocity` term of pure Reynolds would damp this naturally, but it's absent here (see §11.2). | Not mitigated. Future options: add Reynolds steering (see §12) or an explicit damping term. |
| **Oscillation**           | At equilibrium, opposing forces cause vibration around a fixed point.                                       | Not mitigated. Velocity dead-zone possible.                      |
| **Local optima**          | A flock may split into stable sub-clusters with no mechanism to reunite them.                              | Not mitigated. "Wandering" rule or global attractor possible.    |
| **No boundaries**         | The pure Reynolds model doesn't define what happens at the world edge.                                      | ✅ **Resolved outside the model** via `boundaryRepulsion` (see §5.4 / §11.5). |
| **O(N²) complexity**      | Every tick, each agent scans all others to find its neighbors.                                              | Not mitigated in v0.1. Spatial hashing / k-d tree / octree post-MVP. |
| **No obstacle avoidance** | The model only considers other agents, not the static environment.                                          | Not mitigated. Separate rule with raycast / SDF possible.        |
| **No goal-seeking**       | A flock has no objective. It moves but goes nowhere in particular.                                          | Not mitigated. Leader, waypoint, or external attraction force possible. |
| **Timestep sensitivity**  | Numerical integration (`p += v·dt`) becomes unstable at large `dt`.                                          | Mitigated: `dt` fixed at 33 ms by the 30 Hz loop. Sub-stepping if needed. |
| **2D vs 3D**              | Formulas are identical, but tuning differs: 3D requires lower weights (more space available).               | To tune if we switch to 3D.                                      |
| **No predator**           | Reynolds 1987 mentions an "avoid predator" rule as a natural extension. | ✅ **Resolved in v0.1** via `predatorFlee` (§5.5). An autonomous predator steers toward the nearest agent at 3 m/s; boids within 15 m flee with inverse-square repulsion (`w_flee = 200`). Caught agents respawn 5 s later far from the predator. |

---

## 9. References

- **Reynolds, C. W.** (1987). *Flocks, herds and schools: A distributed behavioral model.* In *Proceedings of the 14th annual conference on Computer graphics and interactive techniques* (SIGGRAPH '87), pp. 25–34. ACM. [DOI: 10.1145/37401.37406](https://doi.org/10.1145/37401.37406)
  > *Note: the paper is often dated 1986 (submission year) or 1987 (publication year). The "Reynolds 1986" reference is the common convention.*
- Reynolds, C. W. — [Boids — Background and Update](https://www.red3d.com/cwr/boids/) (author's personal page, contains pseudocode and demos).

---

## 10. Implementation notes (gakkel-swarm-simulator)

- Rules are implemented in `BoidsRules.java` (`swarm-server` module).
- Coefficients live in `BoidsConfig.java` (Java 21 record) and will be hot-reloadable post-MVP.
- Unit tests in `BoidsRulesTest.java` cover all 5 rules in isolation (separation, alignment, cohesion, boundaryRepulsion, predatorFlee).
- The simulation loop applies all 5 rules at 30 Hz; the `Predator` domain class handles steering, eating, and respawn scheduling.

---

## 11. Implementation choices and deviations from the pure model

The implementation differs from Reynolds' theoretical model on 5 points. This section is for the reader comparing the code with the original paper.

### 11.1 Separation: 1/d² instead of 1/d

| | Pure Reynolds | Implementation |
|---|---|---|
| Weighting | `normalize(diff) / d` — linear decay | `away / d²` — quadratic decay |
| Dedicated radius | `SEPARATION_RADIUS` < `NEIGHBOR_RADIUS` | none — all perceived neighbors are processed |

**Why:** Quadratic decay creates a sharper "personal bubble". A neighbor at half the distance exerts 4× the force, not 2×. Direct benefit: repulsion becomes negligible at the edge of `perceptionRadius` (see §3), removing the need for a second `SEPARATION_RADIUS` parameter.

### 11.2 Pure-direction rules — no Reynolds steering formula

Reynolds builds a complete steering vector inside each rule:
```
desired = normalize(desired) * MAX_SPEED
steer   = desired − agent.velocity     ← correction relative to current velocity
steer   = clamp(steer, MAX_FORCE)
```

The implementation returns `normalize(sum)` — a unit direction vector. Weight composition and integration happen outside, in `steer()` and the simulation loop.

**Corollary:** division by `n` (averaging) before normalization, present in Reynolds, is omitted — `normalize(sum)` and `normalize(sum/n)` give the same result (direction is invariant under positive scaling).

**Why:** Separation of concerns. Rules express *where to go*, not *how to accelerate*. Each rule is unit-testable without simulating a current velocity.

### 11.3 Single radius, pre-filtered neighbors

Reynolds distinguishes two zones: `SEPARATION_RADIUS` (repulsion) < `NEIGHBOR_RADIUS` (alignment + cohesion).
The implementation has a single `perceptionRadius` — the neighbor list is filtered **upstream** by the perception layer before reaching the rules.

**Why:** The 1/d² decay replaces the dedicated separation radius. One parameter to tune. Rules remain stateless on the notion of distance.

### 11.4 No MAX_FORCE — implicit bound from normalization

Reynolds clamps each steering vector to `MAX_FORCE`.
The implementation has no such parameter.

**Why:** Each rule returns a unit vector (magnitude = 1). The total magnitude of `steer` is therefore **upper-bounded** by the sum of weights: `1.5 + 1.0 + 1.0 + 2.0 = 5.5`. This bound is only reached when all vectors are collinear; in practice the actual magnitude is much lower. Explicit clamping adds nothing in this design.

### 11.5 Fourth rule: boundaryRepulsion (addition)

The pure 3-rule model doesn't handle boundaries (see §8). The implementation adds `boundaryRepulsion()` (see §5.4), exposed via a `steer(agent, neighbors, world)` overload.

**Why:** Practical necessity. Without edge repulsion, all agents leave the simulated world within seconds. The overload without `world` is kept for unit tests that don't need a world.

### Summary of deviations

| Deviation | Pure Reynolds | Implementation | Reason |
|---|---|---|---|
| Separation | 1/d, dedicated radius | 1/d², all neighbors | Sharper bubble, single radius |
| Steering | `desired − velocity` + clamp | pure normalized direction | Separation of concerns |
| Radii | 2 (`SEPARATION_RADIUS` + `NEIGHBOR_RADIUS`) | 1 (`perceptionRadius`) | 1/d² compensates |
| MAX_FORCE | explicit parameter | implicit bound from weights | Structurally bounded |
| Boundaries | not handled | `boundaryRepulsion` (4th rule) | Practical necessity |

---

## 12. Returning to pure Reynolds — effort and gains

### 12.1 Estimated effort

Returning to the pure model is a mechanical refactor. File-by-file changes:

**`BoidsConfig.java`** — add 2 fields:
- `separationRadius` (double, validation > 0 and < perceptionRadius)
- `maxForce` (double, validation > 0)

**`BoidsRules.java`** — 3 methods modified:

| Method | Change |
|---|---|
| `separation(agent, neighbors)` | Filter by `separationRadius`. Replace `1/d²` with `1/d`. Add the Reynolds formula: `normalize(sum) * maxSpeed − agent.velocity()`, then `clamp(maxForce)`. |
| `alignment(neighbors)` | Add the `agent` parameter. Divide by `n` before normalizing. Apply `normalize * maxSpeed − velocity`, then `clamp`. |
| `cohesion(agent, neighbors)` | Divide by `n` (already done for the centroid). Apply `normalize * maxSpeed − velocity`, then `clamp`. |
| `steer(agent, neighbors)` | Simplify — rules already return the full steer, no need to multiply by weights... or keep weights at this level (both are valid). |

**`BoidsRulesTest.java`** — update signatures and assertions (tests become richer: we can check that steer is bounded by `maxForce`, and that current velocity influences the result).

**Total estimate: 2 to 3 hours**, most of it on tests. Main risk: retuning coefficients — visual behavior will change.

### 12.2 Expected gains

| Gain | Description |
|---|---|
| **Natural deceleration** | The `desired − velocity` term acts as an implicit damper. If the agent is already heading in the right direction at the right speed, the correction is near-zero. Reduces overshooting and oscillation around the centroid. |
| **Explicitly controllable maneuverability** | `MAX_FORCE / MAX_SPEED` becomes the boid's "turning radius". Fluidity can be tuned without touching Boids weights. |
| **Sharp clean-zone separation** | With `SEPARATION_RADIUS` < `perceptionRadius`, agents only repel within their immediate bubble. Outside that zone, cohesion and alignment aren't perturbed by residual micro-repulsion. |
| **Behavior scalable to neighbor count** | Dividing by `n` in alignment/cohesion prevents an agent in the middle of a dense flock from receiving a "diluted" steer direction. A large flock behaves like a small one. |
| **Conformance to the original paper** | The code maps directly to the Reynolds 1987 pseudocode — easier to explain, debug, and extend ("avoid predator" rule, arrival behavior, etc.). |
| **More expressive tests** | With `MAX_FORCE` bounded, we can write `assertThat(steer.magnitude()).isLessThanOrEqualTo(maxForce)` — a safety invariant absent today. |

### 12.3 What wouldn't change

- The `boundaryRepulsion` rule remains a necessary out-of-model addition.
- O(N²) perception complexity is unchanged — that's a separate problem (spatial hashing, post-MVP).
- Global emergent behavior (flocking) would stay similar — current deviations don't prevent flocking, they just make it theoretically less precise.

### 12.4 Recommendation

Implement the pure version **before** adding SAR — Search and Rescue — application behaviors (leader, waypoint, obstacle avoidance, arrival behavior on a target). The `desired − velocity` term is particularly useful for arrival rules — without it, an agent rushing toward a waypoint has no natural deceleration mechanism.
