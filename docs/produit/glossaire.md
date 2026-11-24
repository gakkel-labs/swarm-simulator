# Gakkel glossary

> Version française : [`glossaire.fr.md`](glossaire.fr.md)

This page fixes the words of the project. Everybody uses the same word for the same
thing: in the docs, on the screens, in the issues and in the code. These words will
become the objects of the code (Domain-Driven Design calls this the *ubiquitous
language*).

Each definition is short and written for non-technical readers. The column **In the
code** says if the word already exists in the code today (✅), or if it is planned (🔜).

More about the user: [`persona-operateur.md`](persona-operateur.md).

---

## 1. The mission

| Word | Definition | In the code |
|------|------------|:-----------:|
| **Mission** | A job given to drone fleets, with an objective, a search area and a time window. Example: find the wreck of an aircraft in a given search area. | 🔜 |
| **Campaign** | A sequence of missions that the operator prepares together. A campaign holds several missions; each mission keeps its own objective, search area and time window. Example: search a large region in several missions, one search area after another. Planned for later. | 🔜 |
| **Reference mission** | The mission used to build and test Gakkel first: the **civil search for a sunken wreck** (aircraft or ship). | — |
| **Other missions** | Missions Gakkel must also handle later: **inspection**, **seabed mapping (bathymetry)**, **mine countermeasures**, **scientific exploration** (take samples, deploy or recover instruments, measure the water). | — |
| **Objective** | What the mission must achieve. Example: "find the wreck", "map this area". | 🔜 |
| **Operator** | The person who prepares and follows the mission from the operations centre. Gakkel helps the operator; the operator decides. | — |
| **Operations centre** | The room (often on a research vessel) where the operator works. It receives the information sent by the drone carriers. | — |
| **Mission state** | Where the mission is in its life. Proposed states: *In preparation*, *Validated* (plan accepted), *In progress* (drones at sea), *Finished*, *Cancelled*. | 🔜 |
| **Mission phases** | The key moments of a mission for the operator: **Prepare**, **Launch**, **Detect**, **Confirm**, **Intervene**, **Close**. | 🔜 |
| **GO / NO-GO** | The decision to launch, or not, depending on the weather and sea traffic. | 🔜 |
| **Weather window** | The period when the weather allows the drones to be launched and recovered. | 🔜 |
| **AIS** | The system that gives the position of nearby ships. The operator checks it before launch. | 🔜 |
| **Save / open a mission** | The operator can save a mission and open it again later, to reuse or change it. | 🔜 |
| **Advance preparation** | Preparing a mission at the base, before leaving: the operator plans the mission ahead and decides which drones and equipment to load. | 🔜 |
| **On-site preparation** | Preparing a mission on site, at sea: the operator opens a mission prepared in advance and adapts it to the real situation (weather, drones really on board, a failure). | 🔜 |
| **Mission report** | The final record: covered areas, contacts and their status, decisions taken. | 🔜 |

## 2. The fleet and the drones

| Word | Definition | In the code |
|------|------------|:-----------:|
| **Drone** | A machine without a crew. In Gakkel, the drones are underwater drones. A fleet uses detection drones and specialised drones. | ✅ (*agent*) |
| **Fleet** | One team that works together: **one drone carrier + one swarm + one or more specialised drones**. The operator coordinates several fleets. | 🔜 |
| **Drone carrier** | It carries the swarm, **drops** it in the search area, talks to the swarm and relays information to the operations centre. It also deploys the specialised drones. | 🔜 (type reserved) |
| **Swarm** | A group of **detection drones** that move together, with no leader. The swarm does the **first detection** over the search area. | ✅ |
| **Detection drone** | A drone of the swarm. It explores the search area with its sensors and reports what it finds. | ✅ (*agent* `EXPLORER`) |
| **Specialised drone** | A drone sent by the drone carrier for a precise job on site. It has one role or several roles, for example **qualify a contact** (check what it is) or **intervene** on it (recover the black box, neutralise a mine). Later, other roles: **take samples** or **deploy an instrument** (and recover it). | 🔜 (type reserved) |
| **Drone pool** | All the drones available at the base, with their state: **ready**, **in maintenance** or **spare**. A drone can fail during a mission (or during a simulation); the backend can then propose **reserve drones** to take with the fleet. | 🔜 |
| **Fleet configuration** | The choice of drones for a mission: how many, which types, which settings. The backend can test many configurations and propose the best one. But this is a help, not a must: the operator can also choose the configuration themselves. Today it holds the number of detection drones and the search pattern. | ✅ (`FleetConfiguration`) |

## 3. How a fleet works: Drop, Detect, Confirm, Intervene

| Word | Definition | In the code |
|------|------------|:-----------:|
| **Drop** | Step 1. The drone carrier drops the swarm in the search area. | 🔜 |
| **Detect** | Step 2. The swarm, now in the water, explores the search area with its sensors. When a detection drone sees a suspect object, it reports a **contact** to the drone carrier, which relays it to the operator. At this stage a contact is only a *possible* target: nobody knows yet if it is the wreck or a rock. | ✅ (simple version) |
| **Confirm** | Step 3. The drone carrier deploys a specialised drone to go and look at one contact up close. Detect says "there is something here"; Confirm says "yes, it is the wreck" or "no, it is not". The operator gives the order, or lets Gakkel do it automatically. | 🔜 |
| **Intervene** | Step 4. A specialised drone takes care of the confirmed contact: for example it recovers the black box or neutralises a mine. Confirm says "it is the wreck"; Intervene does something about it. | 🔜 |
| **Manual / automatic dispatch** | Chosen during preparation: the operator sends each specialised drone themselves, or Gakkel sends it automatically. The operator can take back control at any time. | 🔜 |

## 4. The search

| Word | Definition | In the code |
|------|------------|:-----------:|
| **Search area** | The part of the sea to search, often 50 to 100 km². | ✅ (*world*) |
| **Search pattern** | The way the drones move to cover the search area. It is a setting of the fleet configuration. Patterns planned for step 2: **lawnmower** (parallel lines), **Boids** (free flocking), **lawnmower + Boids**, **ant colony** (drones avoid places already searched) and **ant colony + Boids**. Today only Boids exists. | ✅ (`SearchPattern`, `BoidsSearchPattern`) |
| **Coverage** | The part of the search area already searched. The goal is no gaps and no overlaps. | 🔜 |
| **Target** | In the simulation, the object to find (the "wreck"). Today the operator places it with a click in the viewer. | ✅ |
| **Contact** | Something a drone has detected and that could be the target. A contact must be checked. Status: *to review*, *confirmed*, *dismissed*. | 🔜 |
| **Detection** | The moment a drone passes close enough to the target to "see" it. | ✅ |
| **Sensor radius** | The distance at which a drone can detect the target (10 m in the live demo). It is the range of the drone's search sensor, read from its drone specification. Replaced by the side-scan sonar band in the realistic scale ([ADR-0011](../adr/0011-model-assumptions-and-scale.md)). | ✅ |
| **Sonar** | The underwater sensor that "sees" with sound. Simulated later. | 🔜 |
| **Side-scan sonar** | The search sensor of a detection drone. It sees a band of seabed on each side (50 m), not right under the drone (blind gap), and its detection chance falls with range. | 🔜 |
| **Swath** | The band of seabed a search sensor sees as the drone moves. The lawnmower spaces its lines from it ([diagram](#diagram-swath-nadir-and-line-spacing)). | 🔜 |
| **Nadir** | The point of the seabed right under the drone. The side-scan sonar does not see around the nadir: this is the gap the next line must cover ([diagram](#diagram-swath-nadir-and-line-spacing)). | 🔜 |
| **Working altitude** | The height above the seabed where a search sensor works well (15 m for the side-scan sonar). | 🔜 |
| **Seabed following** | The drone keeps its working altitude: if the seabed rises, the drone rises. The search pattern only decides the horizontal moves. | 🔜 |
| **Forward obstacle sonar** | A navigation sensor that sees a cone in front of the drone (about 100 m). A drone sees an obstacle only there. | 🔜 |
| **Drone specification** | The technical values of a drone model: speeds, turn rate, working altitude, endurance, sensors, acoustic modem. A value object: one per drone model ([ADR-0011](../adr/0011-model-assumptions-and-scale.md)). | ✅ (`DroneSpecification`) |
| **Drone model** | A kind of drone, with its own name and drone specification, for example `side-scan-drone`. A fleet configuration says which models and how many of each: a swarm can mix several models. | ✅ |
| **Drone catalog** | The list of drone models available, each with its drone specification. It is a configuration file, not code: adding a model means adding one entry, with no rebuild ([format](../drone-catalog.md)). Later, the operator will edit it from the control station. | ✅ (`DroneCatalog`) |
| **Acoustic link** | The underwater radio-like link (with sound) between the swarm and the drone carrier. It is slow and can be cut. | 🔜 |
| **Acoustic modem** | The device each drone uses to send its position by sound (every 10 s, range about 1 km, some messages lost). Boids uses these announced positions. | 🔜 |
| **Score** | The **success probability** of a fleet configuration: the share of successful scenarios in the common set of scenarios. Example: "succeeds in 460 scenarios out of 500" = 92%. Used by the backend to compare configurations ([problem sheet](problem-find-a-wreck.md)). | 🔜 |
| **Common set of scenarios** | The list of scenarios every fleet configuration is simulated on: the wreck at position A, B, C… (spread across the search area), plus scenarios with no wreck. Each scenario has its seed. All configurations run on the same scenarios, so the comparison is fair. | 🔜 |
| **Share of no-wreck scenarios** | The share of the scenario set where the wreck is not in the search area. The operator sets it in the mission, depending on their doubt (20% by default). | 🔜 |
| **Limit time** | The time when the mission must be over (drone energy, weather). Going over it is a failure. | 🔜 |
| **Estimated time** | The expected duration of a mission: the time under which 9 successful scenarios out of 10 end. Going over it makes the mission late, not failed. | 🔜 |
| **Coverage threshold** | The coverage to reach to show that the wreck is not in the search area. Chosen by the operator (95% by default). | 🔜 |

### Diagram: swath, nadir and line spacing

As a reminder, what the side-scan sonar of a detection drone sees, then how the lawnmower places its lines so that the nadir of one line is seen by its neighbours. Details in [ADR-0011](../adr/0011-model-assumptions-and-scale.md).

```
          front view, the drone comes towards us

                       ●  drone
                      /|\        working altitude: 15 m
                     / | \
                    /  |  \
  //////////////////   |   //////////////////   seabed
  |<--- range ---->| nadir |<--- range ---->|
  |  15 to 50 m    | 2x15 m|    15 to 50 m  |
  |<----------------- swath ---------------->|
```

```
Cross-section of three neighbouring lines (1 character = 5 m, # = seen by the sonar)

              -50       0         50        100 m
              |         |         |         |
line C           #######                      drone at -50 m
line A        #######      #######            drone at 0 m
line B                  #######      #######  drone at 50 m
                     ^^^^^^  nadir of line A: seen by C and B
```

## 5. Swarm behaviour (Boids)

| Word | Definition | In the code |
|------|------------|:-----------:|
| **Boids** | An algorithm by Craig Reynolds (1986): each drone follows simple rules with its close neighbours, and a group behaviour appears. No leader. | ✅ |
| **Separation** | Rule: don't get too close to your neighbours. | ✅ |
| **Alignment** | Rule: go in the same direction as your neighbours. | ✅ |
| **Cohesion** | Rule: stay close to the group. | ✅ |
| **Obstacle** | Something in the search area that drones must avoid (a sphere in the simulation). | ✅ |
| **Boundary** | The edges of the search area (in the simulation, the edges of the world). Drones are pushed back when they get too close. | ✅ |
| **Danger** | Something the drones must flee or keep away from, because it can hurt or block them: a surface ship, a drifting fishing net, a large animal, a no-go area. Used to test how the swarm reacts. It can be switched ON or OFF in the simulation. | ✅ (*predator*, renamed soon) |
| **Wander** | A little random movement so the swarm does not look mechanical. | ✅ |

Details and formulas: [`../01-boids-rules.md`](../01-boids-rules.md).

## 6. Technical words

| Word | Definition | In the code |
|------|------------|:-----------:|
| **Backend** | The Java program that runs the simulation. It is the only one that decides what happens. | ✅ |
| **Viewer** (Unity) | The 3D display. It will let you follow a mission live and replay it afterwards, watching the drones move. It only shows what the backend sends; it decides nothing. | ✅ |
| **Control station** | The operator's web application, where the mission is defined (repository `mission-console`). The operator can create a mission there alone, from start to end, without using the simulation. | 🔜 |
| **Detailed view / simple view** | The two looks of the viewer. The detailed view shows 3D models and a real seabed, to present the project; the simple view shows plain shapes, to read the swarm behaviour. The V key switches ([ADR-0006](../adr/0006-detailed-and-simple-view.md)). | ✅ |
| **Replay** | Play again a mission that is finished, in the viewer, to see how the drones moved and what happened. | 🔜 |
| **Simulation** | A computer run that imitates the mission, to test it before going to sea. It helps the operator; it is not a required step. | ✅ |
| **Agent** | The code name of a drone in the simulation. Its type says its role: `EXPLORER` (detection drone), `SPECIALIST` (specialised drone, type only reserved), `CARRIER` (drone carrier). | ✅ |
| **Tick** | One step of the simulation. The backend computes 30 ticks per second. | ✅ |
| **WorldState** | A "photo" of the simulation at one moment: drones, obstacles, danger, target. The backend sends one 20 times per second. | ✅ |
| **gRPC stream** | The connection that carries the WorldState from the backend to the viewer, non-stop. | ✅ |
| **Seed** | A number that sets the random choices. The same seed gives the same starting positions. | ✅ |
| **Simulation clock** | A time that belongs to the simulation, not to the computer. It will make runs repeatable and faster than real time. | 🔜 |
| **Optimizer** | The part of the backend that runs many simulations and picks the best fleet configuration using the score. | 🔜 |
| **NED** | The direction system used on the wire between the backend and the viewer: North, East, Down (depth). Inside, the backend uses East, Up, North ([ADR-0007](../adr/0007-coordinate-frames.md)). | ✅ |
