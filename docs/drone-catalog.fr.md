# Catalogue de drones

> Les modèles de drones qu'une flotte peut utiliser, et comment en ajouter un sans recompiler.
> English version: [`drone-catalog.md`](drone-catalog.md).

## Ce que c'est

Le **catalogue de drones** liste les modèles de drones disponibles, chacun avec sa **fiche
technique** : vitesses, virage, altitude de travail, autonomie, capteur de recherche, capteurs de
navigation et modem acoustique ([ADR-0011](adr/0011-model-assumptions-and-scale.fr.md),
[glossaire](produit/glossaire.fr.md)).

Une **configuration de flotte** choisit des modèles dans le catalogue, par leur nom, avec un
nombre pour chacun. Un essaim peut mélanger plusieurs modèles, par exemple 6 drones avec un sonar
latéral et 4 drones plus rapides.

```
drone-catalog-demo.json ──lu par──> JsonDroneCatalogRepository (adaptateur)
                                              │
                                              ▼
                                  DroneCatalog (domaine)
                                  nom du modèle -> DroneSpecification
                                              │  modèle inconnu => refus clair
                                              ▼
SWARM_FLEET="side-scan-drone:6,fast-drone:4" ──> FleetConfiguration
                                                  [ DroneGroup(fiche, 6),
                                                    DroneGroup(fiche, 4) ]
```

Le domaine ne connaît que l'interface `DroneCatalogRepository` (un *port*). Aujourd'hui on lit un
fichier JSON ; à l'étape 3, quand l'opérateur modifiera le catalogue depuis le poste de contrôle,
un adaptateur base de données pourra prendre sa place sans changer le domaine ni le moteur.

## Les deux catalogues du serveur

| Fichier | Valeurs | Utilisé par |
|---|---|---|
| `swarm-server/src/main/resources/drone-catalog-demo.json` | la démo en direct : 5 m/s, rayon de détection 10 m (sans nadir) | le serveur par défaut : la démo Unity reste la même qu'avant |
| `swarm-server/src/main/resources/drone-catalog-scoring.json` | ADR-0011 : 1,5 m/s, sonar latéral 50 m de chaque côté, 16 h | les simulations d'un scoring (profil `scoring`, #177), puis l'optimiseur |

Les deux fichiers utilisent **les mêmes noms de modèles** : une configuration de flotte s'écrit de
la même façon dans les deux profils.

## Choisir le catalogue et la flotte

| Variable | Propriété | Défaut |
|---|---|---|
| `SWARM_DRONE_CATALOG` | `swarm.drone-catalog` | `classpath:drone-catalog-demo.json` |
| `SWARM_FLEET` | `swarm.fleet` | vide : `SWARM_AGENT_COUNT` drones du premier modèle du catalogue |

- `classpath:nom.json` lit un fichier dans le jar. Toute autre valeur est un **chemin de
  fichier** : copier un catalogue, ajouter un modèle, et y faire pointer `SWARM_DRONE_CATALOG`.
  Sans recompiler.
- `SWARM_FLEET` s'écrit `modèle:nombre,modèle:nombre`, par exemple `side-scan-drone:6,fast-drone:4`.
- Un modèle absent du catalogue arrête le démarrage avec un message clair :
  `Unknown drone model 'x'. Known models: side-scan-drone`.
- Une valeur fausse dans le catalogue arrête aussi le démarrage, et le message nomme le fichier,
  le modèle et le champ, par exemple
  `drone-catalog.json: drone 'fast-drone': missing number 'movement.cruiseSpeedMetersPerSecond'`.

```bash
SWARM_DRONE_CATALOG=C:/gakkel/my-drones.json SWARM_FLEET=side-scan-drone:6,fast-drone:4 \
  mvn -pl swarm-server exec:java -Dexec.mainClass="fr.gakkel.swarmsimulator.swarmserver.server.SwarmServer"
```

## Format du fichier

Les unités sont dans le nom des champs. Chaque valeur est vérifiée à la lecture du catalogue :
pas de vitesse négative ou nulle, une probabilité entre 0 et 1, un nadir plus petit que la portée
du sonar...

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

| Champ | Sens |
|---|---|
| `model` | Le nom du modèle, unique dans le catalogue |
| `role` | Seulement `detection-drone` pour l'instant (porte-drones et drones spécialisés : #35) |
| `movement.cruiseSpeedMetersPerSecond` | Vitesse pendant la recherche, aussi la vitesse max du drone |
| `movement.maxTurnRateDegreesPerSecond` | Vitesse de virage max |
| `movement.verticalSpeedMetersPerSecond` | Vitesse de plongée et de remontée |
| `movement.workingAltitudeMeters` | Hauteur au-dessus du fond où le capteur de recherche marche |
| `movement.maxDepthMeters` | La profondeur max du drone |
| `enduranceHours` | L'autonomie du drone |
| `searchSensor.type` | Seulement `side-scan-sonar` pour l'instant |
| `searchSensor.rangePerSideMeters` | Portée d'un côté du sonar |
| `searchSensor.nadirHalfWidthMeters` | La moitié de la largeur du trou sous le drone (0 : pas de trou) |
| `searchSensor.detectionProbability` | Chance de détection du bord du nadir jusqu'à `fullProbabilityUntilMeters` |
| `searchSensor.fullProbabilityUntilMeters` | Au-delà, la chance baisse en ligne droite jusqu'à 0 à la portée |
| `searchSensor.falseContactsPerSquareKilometer` | Faux contacts par km² de fond balayé |
| `navigationSensors.altimeterMaxRangeMeters` | Plus grande distance au fond que l'altimètre mesure |
| `navigationSensors.forwardSonar` | Cône d'évitement des obstacles : portée et largeur |
| `acousticModem` | Portée, temps entre deux messages de position, part des messages perdus |

## Ce que le moteur lit aujourd'hui

Seules la vitesse de croisière (la vitesse max de chaque drone) et la portée du capteur de
recherche (le rayon de détection, encore une sphère) servent. Les autres valeurs sont prêtes pour
les issues suivantes : suivi du fond, fauchée du sonar latéral, voisins par le modem acoustique,
sonar frontal (ADR-0011).
