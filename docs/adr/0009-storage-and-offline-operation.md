# ADR-0009: One PostgreSQL database owned by the backend, and a stack that runs offline

[Français](0009-storage-and-offline-operation.fr.md)

## Status
Proposed
Date: 2026-11-23

## Context
At Step 2 the backend runs hundreds of simulations to compute the score of a fleet
configuration (mission success probability, see the
[problem sheet](../produit/problem-find-a-wreck.md)). Today nothing is kept: when the
process stops, the results are lost. We need to keep them, for four reasons:

1. **The web control station**: hundreds of simulations take time. The control station starts
   the computation, then reads the result when it is ready.
2. **No useless work**: same mission + same fleet configuration + same scenarios + same code
   version = same result. It can be reused.
3. **Compare over time**: for example, check that a search pattern gets better from one
   version to the next.
4. **Campaigns**: a later campaign planner will need the results of the previous missions.

Gakkel is used on board a ship. At sea, the internet link (satellite) is slow, expensive or
cut. The whole system must work **without internet**, on one workstation on board.

[ADR-0002](0002-no-spring-boot-for-mvp.md) says that a database is a reason to reconsider a
framework. This ADR adds a database, but not a framework.

## Decision
**One database, owned by the backend.**
- **PostgreSQL**, with the **PostGIS** extension (search areas, positions and, later, S-57
  nautical charts are geographic data).
- Only the Java backend reads and writes it. The web control station never has its own
  database: it always goes through the backend API.

**What we store: summaries, not trajectories.**
- A simulation run: the mission, the fleet configuration (search pattern included), the
  common set of scenarios (one seed per scenario), the code version, the launch time, and who
  launched it (empty for now; filled once the control station has user accounts).
- One result per scenario: success or failure, end time, drones lost (detection,
  specialised, drone carrier).
- The score: success probability, estimated time (the time under which 9 successful
  scenarios out of 10 end) and mean time.
- Full trajectories (for replay) are **not** stored now. They will get their own decision
  with the replay feature.

**Behind a port (DDD).**
- The domain defines an interface, `SimulationResultRepository`. It speaks the glossary
  words and knows nothing about SQL.
- A PostgreSQL adapter implements it with plain JDBC. Schema changes are versioned SQL
  scripts (Flyway).
- Domain tests use an in-memory fake of the port. Adapter tests use a real PostgreSQL started
  by **Testcontainers**.

**How we test the storage.**
- **Unit tests (most of them)**: they use an in-memory fake of `SimulationResultRepository`,
  written by us with a `List` or a `Map`. No database at all: they are fast and run everywhere.
- **Integration tests (a few)**: they test the PostgreSQL adapter with **Testcontainers**. The
  test starts a real PostgreSQL + PostGIS in a Docker container, runs the Flyway scripts, then
  stops it. So Docker is needed on the developer machine (and in CI) to run these tests.
- **No H2 for tests.** H2 is an in-memory database that imitates other databases. It has no
  PostGIS, and its PostgreSQL mode does not accept all PostgreSQL SQL. A test could pass on H2
  and fail on the real database. We test against the same database as in production.

**No secrets in the repository.**
- The database user and password come from environment variables, never from the code.
- A `.env` file holds them on each machine and is never committed (it is in `.gitignore`).
  A `.env.example` file, with fake values, is committed to show which variables are needed.

**Free tools only.**
- Every tool in this decision is free and open source: PostgreSQL, PostGIS, Flyway
  (Community edition), Testcontainers, Docker Engine and Docker Compose.
- On Windows, Docker Desktop is free for personal and non-commercial use (a paid licence is
  needed only for large companies). On a ship workstation under Linux, Docker Engine is
  enough and is always free.

**Offline on a ship workstation.**
- The whole stack (database, backend, web control station) starts with one
  **Docker Compose** file, on one machine, with no internet.
- No online dependency at run time: no CDN (scripts, styles, fonts are packaged with the
  web control station), no online map tiles (S-57 charts are loaded from local files), no
  cloud service.
- Data that usually comes from the internet comes from the ship instead, or is loaded
  before departure: AIS from the ship's own receiver, weather from a file loaded before
  departure.
- Internet is only needed to **install or update** the workstation (download images), not to
  run a mission.

## Consequences
+ Results survive a restart, can be compared over time and reused.
+ One source of truth: no sync between two databases.
+ The domain does not depend on PostgreSQL: the storage can change without touching the
  simulation.
+ Works at sea without internet, which is a real constraint for operators.
+ PostgreSQL + PostGIS is a common industry choice, and it will hold the S-57 charts later.
− Running the backend now needs a database: Docker becomes a tool every developer needs.
− Tests of the adapter are slower (a container starts), so they stay few; most tests use the
  in-memory fake.
− "Offline only" rules out convenient online services (map tiles, hosted database, CDN).
− The code version must be part of the cache key, or an old result could be reused after
  the code changed.

## Alternatives considered
- **JSON files first, a database later**: simpler at the very start, but we would write the
  storage twice. With the port in place, starting with PostgreSQL costs little more.
- **One database for the web, one for the backend**: rejected. Two truths to keep in sync,
  and the web has nothing to store that the backend does not own.
- **H2 only for the tests**: rejected (see "How we test the storage"): no PostGIS, and not
  exactly the same SQL as PostgreSQL.
- **SQLite or H2 (embedded database)**: simpler to install, but weak for geographic data
  and for several users at the same time. Not the industry standard for this kind of system.
- **A cloud database**: rejected, it does not work without internet, and it is not free.
- **Spring Data / JPA**: rejected for now (ADR-0002 stays valid). Plain JDBC keeps the
  wiring visible and is enough for a few tables.
