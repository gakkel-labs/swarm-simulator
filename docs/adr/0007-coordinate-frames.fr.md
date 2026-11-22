# ADR-0007 : Garder le repère du serveur dans le backend et envoyer du NED sur le réseau

[English](0007-coordinate-frames.md)

## Status
Proposed
Date : 2026-11-22

## Contexte
Trois programmes regardent les mêmes drones, et chacun a sa propre habitude pour les axes :

- **Le backend** (`swarm-server`) a été écrit avec un repère Y vers le haut, comme un moteur 3D :
  X = Est, Y = Haut, Z = Nord. Y = 0 est la surface de l'eau, et un drone sous l'eau a un
  Y négatif.
- **Le monde marin** utilise le **NED** (Nord, Est, Bas) : X = Nord, Y = Est, Z = Bas.
  La profondeur est un nombre positif, comme la donnent un capteur de profondeur et une carte marine.
- **Unity** utilise un repère main gauche avec Y vers le haut : X = Est, Y = Haut, Z = Nord.

Pendant un temps, la doc disait « le backend travaille en NED ». C'était faux, et cela rendait
les échanges d'axes difficiles à suivre. Le code a maintenant une seule classe de conversion de
chaque côté : il est temps d'écrire la règle.

## Décision
- Le **contrat gRPC** (`gakkel.swarm.v1`) est en **NED, en mètres**. Chaque `Vec3` sur le réseau
  (positions, vitesses, cible) est en NED.
- Le **domaine du backend** garde son propre repère (Est, Haut, Nord). Il ne sort pas du backend.
- Chaque côté convertit à **un seul endroit** :
  - Java : `CoordinateConversions` (`serverToNed()` / `nedToServer()`), utilisé par
    `WorldStateBuilder` et `SimulationControlImpl`.
  - C# : `CoordinateUtils` (`NedToUnity()` / `UnityToNed()`).
- Aucune autre classe n'échange les axes.

| | X | Y | Z | Surface |
|---|---|---|---|---|
| Backend | Est | Haut | Nord | Y = 0, sous l'eau Y < 0 |
| Contrat (NED) | Nord | Est | Bas | Z = 0, sous l'eau Z > 0 |
| Unity | Est | Haut | Nord | Y = 0, sous l'eau Y < 0 |

```
NED   = (serveur.z, serveur.x, -serveur.y)
Unity = (ned.y, -ned.z, ned.x)
```

Avec le monde par défaut (`SWARM_WORLD_WIDTH=100`, `SWARM_WORLD_HEIGHT=100`,
`SWARM_WORLD_DEPTH=50`), les drones se déplacent sur Est `[0, 100]` m, Bas `[0, 100]` m et
Nord `[0, 50]` m. Attention : dans le backend, « height » est la colonne d'eau (Haut/Bas) et
« depth » est l'étendue vers le Nord. Ces noms prêtent à confusion ; les renommer est noté dans
la dette technique.

## Conséquences
+ Le réseau parle la langue du monde marin : de vraies données de navigation (compas, capteur de
  profondeur, positions sur la carte) pourront arriver plus tard sans nouveau contrat.
+ Unity et le backend ont les mêmes axes : la conversion C# est le miroir de la conversion Java,
  et chacune est testée de son côté.
+ Un bug d'axes n'a que deux endroits où chercher.
+ La taille de la zone de recherche passe aussi en NED sur le réseau
  (`WorldState.search_area_size_m`, convertie par `CoordinateConversions`) : Unity peut placer le
  fond marin sans connaître les noms du backend.
− Deux repères dans le projet au lieu d'un : un nouveau lecteur doit connaître le tableau ci-dessus.
− Les noms « height » et « depth » du backend ne disent pas ce qu'ils sont, tant qu'ils ne sont pas renommés.

## Alternatives considérées
- **Passer le domaine du backend en NED** : rejetée pour l'instant. Cela touche toutes les règles
  Boids, les limites et les tests, sans rien changer au comportement. Cela pourra venir plus tard,
  avec sa propre ADR, si le backend lit de vraies données de navigation.
- **Envoyer le repère Unity sur le réseau** : rejetée. Le contrat suivrait une vue 3D au lieu
  de la convention marine, et le poste de contrôle aurait besoin d'une conversion de plus.
- **Convertir dans chaque classe qui en a besoin** : rejetée. C'est justement comme ça que la doc
  et le code se sont éloignés.
