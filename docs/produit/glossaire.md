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
| **Mission** | A job given to drone fleets, with an objective, an area and a time window. Example: find the wreck of an aircraft in a given area. | 🔜 |
| **Reference mission** | The mission used to build and test Gakkel first: the **civil search for a sunken wreck** (aircraft or ship). | — |
| **Other missions** | Missions Gakkel must also handle later: **inspection**, **seabed mapping**, **mine countermeasures**. | — |
| **Objective** | What the mission must achieve. Example: "find the wreck", "map this area". | 🔜 |
| **Operator** | The person who prepares and follows the mission from the operations centre. Gakkel helps the operator; the operator decides. | — |
| **Operations centre** | The room (often on a research vessel) where the operator works. It receives the information sent by the drone carriers. | — |
| **Mission state** | Where the mission is in its life. Proposed states: *In preparation*, *Validated* (plan accepted), *In progress* (drones at sea), *Finished*, *Cancelled*. | 🔜 |
| **Mission phases** | The key moments of a mission for the operator: **Prepare**, **Launch**, **Detect**, **Confirm**, **Close**. | 🔜 |
| **GO / NO-GO** | The decision to launch, or not, depending on the weather and sea traffic. | 🔜 |
| **Weather window** | The period when the weather allows the drones to be launched and recovered. | 🔜 |
| **AIS** | The system that gives the position of nearby ships. The operator checks it before launch. | 🔜 |
| **Mission report** | The final record: covered areas, contacts and their status, decisions taken. | 🔜 |

## 2. The fleet and the drones

| Word | Definition | In the code |
|------|------------|:-----------:|
| **Drone** | A machine without a crew. In Gakkel, the drones are underwater drones. A fleet uses detection drones and specialised drones. | ✅ (*agent*) |
| **Fleet** | One team that works together: **one drone carrier + one swarm + one or more specialised drones**. The operator coordinates several fleets. | 🔜 |
| **Drone carrier** | It carries the swarm, **drops** it in the area, talks to the swarm and relays information to the operations centre. It also deploys the specialised drones. | 🔜 (type reserved) |
| **Swarm** | A group of **detection drones** that move together, with no leader. The swarm does the **first detection** over the area. | ✅ |
| **Detection drone** | A drone of the swarm. It searches the area with its sensors and reports what it finds. | ✅ (*agent* `EXPLORER`) |
| **Specialised drone** | A drone sent by the drone carrier for a precise job on site. It has one role or several roles, for example **qualify a contact** (check what it is) or **recover** an object. | 🔜 |
| **Fleet configuration** | The choice of drones for a mission: how many, which types, which settings. The backend tests many configurations and proposes the best one. | 🔜 |

## 3. How a fleet works: Drop, Detect, Confirm

| Word | Definition | In the code |
|------|------------|:-----------:|
| **Drop** | Step 1. The drone carrier drops the swarm in the search area. | 🔜 |
| **Detect** | Step 2. The swarm, now in the water, searches the area with its sensors. When a detection drone sees a suspect object, it reports a **contact** to the drone carrier, which relays it to the operator. At this stage a contact is only a *possible* target: nobody knows yet if it is the wreck or a rock. | ✅ (simple version) |
| **Confirm** | Step 3. The drone carrier deploys a specialised drone to go and look at one contact up close. Detect says "there is something here"; Confirm says "yes, it is the wreck" or "no, it is not". The operator gives the order, or lets Gakkel do it automatically. | 🔜 |
| **Manual / automatic dispatch** | Chosen during preparation: the operator sends each specialised drone themselves, or Gakkel sends it automatically. The operator can take back control at any time. | 🔜 |

## 4. The search

| Word | Definition | In the code |
|------|------------|:-----------:|
| **Area** (search area) | The zone of sea to search, often 50 to 100 km². | ✅ (*world*) |
| **Search pattern** | The way the drones move to cover the area (for example parallel lines, spiral, or free flocking). Today the swarm only flocks; real search patterns are planned. | 🔜 |
| **Coverage** | The part of the area already searched. The goal is no gaps and no overlaps. | 🔜 |
| **Target** | In the simulation, the object to find (the "wreck"). Today the operator places it with a click in the viewer. | ✅ |
| **Contact** | Something a drone has detected and that could be the target. A contact must be checked. Status: *to review*, *confirmed*, *dismissed*. | 🔜 |
| **Detection** | The moment a drone passes close enough to the target to "see" it. | ✅ |
| **Sensor radius** | The distance at which a drone can detect the target (10 m today). | ✅ |
| **Sonar** | The underwater sensor that "sees" with sound. Simulated later. | 🔜 |
| **Acoustic link** | The underwater radio-like link (with sound) between the swarm and the drone carrier. It is slow and can be cut. | 🔜 |
| **Score** | A number that says how good a fleet configuration is for a mission (for example: time to find the target, coverage reached). Used by the backend to compare configurations. | 🔜 |

## 5. Swarm behaviour (Boids)

| Word | Definition | In the code |
|------|------------|:-----------:|
| **Boids** | An algorithm by Craig Reynolds (1986): each drone follows simple rules with its close neighbours, and a group behaviour appears. No leader. | ✅ |
| **Separation** | Rule: don't get too close to your neighbours. | ✅ |
| **Alignment** | Rule: go in the same direction as your neighbours. | ✅ |
| **Cohesion** | Rule: stay close to the group. | ✅ |
| **Obstacle** | Something in the area that drones must avoid (a sphere in the simulation). | ✅ |
| **Boundary** | The edges of the area. Drones are pushed back when they get too close. | ✅ |
| **Predator** | A hunter that chases the drones; nearby drones flee. Used to test how the swarm reacts to a threat. | ✅ |
| **Wander** | A little random movement so the swarm does not look mechanical. | ✅ |

Details and formulas: [`../01-boids-rules.md`](../01-boids-rules.md).

## 6. Technical words

| Word | Definition | In the code |
|------|------------|:-----------:|
| **Backend** | The Java program that runs the simulation. It is the only one that decides what happens. | ✅ |
| **Viewer** (Unity) | The 3D display. It only shows what the backend sends; it decides nothing. | ✅ |
| **Web station** | The operator's web application, where the mission is defined (repository `mission-console`). | 🔜 |
| **Simulation** | A computer run that imitates the mission, to test it before going to sea. | ✅ |
| **Agent** | The code name of a drone in the simulation. Its type says its role: `EXPLORER` (detection drone), `CARRIER` (drone carrier). Careful: the type `OPERATOR` in the code is only reserved and is **not** the human operator. | ✅ |
| **Tick** | One step of the simulation. The backend computes 30 ticks per second. | ✅ |
| **WorldState** | A "photo" of the simulation at one moment: drones, obstacles, predator, target. The backend sends one 20 times per second. | ✅ |
| **gRPC stream** | The connection that carries the WorldState from the backend to the viewer, non-stop. | ✅ |
| **Seed** | A number that sets the random choices. The same seed gives the same starting positions. | ✅ |
| **Simulation clock** | A time that belongs to the simulation, not to the computer. It will make runs repeatable and faster than real time. | 🔜 |
| **Optimizer** | The part of the backend that runs many simulations and picks the best fleet configuration using the score. | 🔜 |
| **NED** | The direction system used by the backend: North, East, Down (depth). | ✅ |
