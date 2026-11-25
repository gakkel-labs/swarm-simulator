# Drone catalog

> The drone models a fleet can use, and how to add one without a rebuild.
> Version française : [`drone-catalog.fr.md`](drone-catalog.fr.md).

## What it is

The **drone catalog** lists the drone models available, each with its **drone specification**:
speeds, turn rate, working altitude, endurance, search sensor, navigation sensors and acoustic
modem ([ADR-0011](adr/0011-model-assumptions-and-scale.md), [glossary](produit/glossaire.md)).

A **fleet configuration** picks models from the catalog by name, with a count for each. A swarm
can mix several models, for example 6 drones with a side-scan sonar and 4 faster drones.

```
drone-catalog-demo.json ──read by──> JsonDroneCatalogRepository (adapter)
                                              │
                                              ▼
                                  DroneCatalog (domain)
                                  model name -> DroneSpecification
                                              │  unknown model => clear refusal
                                              ▼
SWARM_FLEET="side-scan-drone:6,fast-drone:4" ──> FleetConfiguration
                                                  [ DroneGroup(specification, 6),
                                                    DroneGroup(specification, 4) ]
```

The domain only knows the `DroneCatalogRepository` interface (a *port*). Today a JSON file is
read; at Step 3, when the operator edits the catalog from the control station, a database adapter
can take its place without changing the domain or the engine.

## The two catalogs of the server

| File | Values | Used by |
|---|---|---|
| `swarm-server/src/main/resources/drone-catalog-demo.json` | the live demo: 5 m/s, detection radius 10 m (no nadir) | the server by default, so the Unity demo looks the same as before |
| `swarm-server/src/main/resources/drone-catalog-scoring.json` | ADR-0011: 1.5 m/s, side-scan sonar 50 m per side, 16 h | the simulations of a scoring (profile `scoring`, #177), then the optimizer |

Both files use **the same model names**: a fleet configuration is written the same way in both
profiles.

## Choosing the catalog and the fleet

| Variable | Property | Default |
|---|---|---|
| `SWARM_DRONE_CATALOG` | `swarm.drone-catalog` | `classpath:drone-catalog-demo.json` |
| `SWARM_FLEET` | `swarm.fleet` | unset: `SWARM_AGENT_COUNT` drones of the first model of the catalog |

- `classpath:name.json` reads a file inside the jar. Any other value is a **file path**: copy a
  catalog, add a model, and point `SWARM_DRONE_CATALOG` to it. No rebuild.
- `SWARM_FLEET` is `model:count,model:count`, for example `side-scan-drone:6,fast-drone:4`.
- A model missing from the catalog stops the start with a clear message:
  `Unknown drone model 'x'. Known models: side-scan-drone`.
- A wrong value in the catalog also stops the start, and the message names the file, the model
  and the field, for example
  `drone-catalog.json: drone 'fast-drone': missing number 'movement.cruiseSpeedMetersPerSecond'`.

```bash
SWARM_DRONE_CATALOG=C:/gakkel/my-drones.json SWARM_FLEET=side-scan-drone:6,fast-drone:4 \
  mvn -pl swarm-server exec:java -Dexec.mainClass="fr.gakkel.swarmsimulator.swarmserver.server.SwarmServer"
```

## File format

Units are in the field names. Every value is checked when the catalog is read: no negative or
zero speed, a probability between 0 and 1, a nadir smaller than the sonar range...

```json
{
  "drones": [
    {
      "model": "side-scan-drone",
      "role": "detection-drone",
      "movement": {
        "cruiseSpeedMetersPerSecond": 1.5,
        "maxTurnRateDegreesPerSecond": 10,
        "verticalSpeedMetersPerSecond": 0.5,
        "workingAltitudeMeters": 15,
        "maxDepthMeters": 6000
      },
      "enduranceHours": 16,
      "searchSensor": {
        "type": "side-scan-sonar",
        "rangePerSideMeters": 50,
        "nadirHalfWidthMeters": 15,
        "detectionProbability": 0.9,
        "fullProbabilityUntilMeters": 35,
        "falseContactsPerSquareKilometer": 0.5
      },
      "navigationSensors": {
        "altimeterMaxRangeMeters": 200,
        "forwardSonar": { "rangeMeters": 100, "fieldOfViewDegrees": 120 }
      },
      "acousticModem": {
        "rangeMeters": 1000,
        "messagePeriodSeconds": 10,
        "messageLossRate": 0.15
      }
    }
  ]
}
```

| Field | Meaning |
|---|---|
| `model` | The name of the model, unique in the catalog |
| `role` | Only `detection-drone` for now (drone carrier and specialised drones: #35) |
| `movement.cruiseSpeedMetersPerSecond` | Speed while searching, also the top speed of the drone |
| `movement.maxTurnRateDegreesPerSecond` | How fast the drone can turn |
| `movement.verticalSpeedMetersPerSecond` | Dive and ascent speed |
| `movement.workingAltitudeMeters` | Height above the seabed where the search sensor works |
| `movement.maxDepthMeters` | The deepest the drone can go |
| `enduranceHours` | How long the drone can work |
| `searchSensor.type` | Only `side-scan-sonar` for now |
| `searchSensor.rangePerSideMeters` | Range of one side of the sonar |
| `searchSensor.nadirHalfWidthMeters` | Half the width of the gap under the drone (0: no gap) |
| `searchSensor.detectionProbability` | Chance of detection from the nadir edge to `fullProbabilityUntilMeters` |
| `searchSensor.fullProbabilityUntilMeters` | After this distance, the chance drops in a straight line to 0 at the range |
| `searchSensor.falseContactsPerSquareKilometer` | False contacts per km² of seabed swept |
| `navigationSensors.altimeterMaxRangeMeters` | Largest distance to the seabed the altimeter measures |
| `navigationSensors.forwardSonar` | Obstacle avoidance cone: range and width |
| `acousticModem` | Range, time between two position messages, share of messages lost |

## What the engine reads today

Only the cruise speed (the speed limit of each drone) and the range of the search sensor (the
detection radius, still a sphere) are used. The other values are ready for the next issues:
seabed following, side-scan swath, acoustic neighbors, forward sonar (ADR-0011).
