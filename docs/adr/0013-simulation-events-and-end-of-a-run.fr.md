# ADR-0013 : Dire ce qui se passe par des événements, et donner une fin à chaque run

[English](0013-simulation-events-and-end-of-a-run.md)

## Status
Proposed
Date : 2026-11-25

## Contexte
Le moteur (`Simulation`) a été écrit pour la démo en direct. Il écrivait des logs quand un drone
était perdu ou que la cible était trouvée, faisait revenir les drones mangés par le prédateur, et
tournait sans fin. La cible n'existait qu'après un clic de l'opérateur dans la vue 3D.

Le score (#82) et l'optimiseur (#83) ont besoin d'autres runs : l'épave posée au départ (ou pas
d'épave), des drones qui restent perdus, et une fin. Ils doivent aussi savoir ce qui s'est passé
(quand l'épave a été détectée, quels drones ont été perdus) sans lire les logs. La démo en direct
doit marcher comme avant (issue #172).

## Décision
1. **Événements de domaine.** `Simulation.step()` renvoie les événements du pas, dans l'ordre :
   `TargetDetected`, `DroneLost` (cause : risque de perte ou prédateur de la démo),
   `DroneRespawned`, `RunEnded`. Ils forment une interface scellée `SimulationEvent` dans
   `domain`, chacun avec son temps de simulation. Le moteur n'écrit plus de log. `SimulationLoop`
   logue les événements de la démo en direct ; le score les comptera, le rejeu les gardera.
2. **Fin d'un run.** `EndConditions` dit quand un run s'arrête :
   - `never()` pour la démo en direct : elle continue, l'opérateur peut placer une nouvelle cible ;
   - `missionEnd(tempsLimite)` pour un run en lot : il s'arrête quand l'épave est détectée, quand
     le temps limite est atteint, ou quand il ne reste plus aucun drone. Si le même pas remplit
     plusieurs raisons, la détection gagne, sauf si ce pas a dépassé le temps limite : le dépasser,
     c'est un échec. Un pas après la fin est refusé, et la boucle en direct ne fait plus avancer
     un run fini.

   Le seuil de couverture sera une raison de plus avec #133, quand la couverture sera calculée.
3. **Scénario.** Un objet-valeur `Scenario` porte la position de l'épave (ou aucune) et la seed.
   L'épave est posée à t = 0, sans tirage au hasard : l'essaim est le même avec ou sans elle.
4. **Options de démo.** `DemoOptions` regroupe ce que seule la démo a : le prédateur et la
   réapparition des drones qu'il mange. Les runs en lot passent `DemoOptions.none()` : `create`
   refuse un run de mission avec des options de démo. La démo en direct les lit dans les
   réglages. Les diagnostics d'essaim et le CSV de cohésion sont déjà dans `SimulationLoop`, que
   les runs en lot n'utilisent pas.

## Conséquences
+ Le score et l'optimiseur peuvent faire tourner le moteur sans effet de démo, et lire le
  résultat dans les événements.
+ Un seul endroit logue la démo en direct, et le moteur reste sans log, sans thread et sans gRPC.
+ Les profils de #177 s'y branchent : `demo` donne les options de démo et `never()`, `batch`
  donne `none()` et `missionEnd`.
− Chaque appel à `Simulation.create` passe maintenant un scénario, des conditions de fin et des
  options de démo.
− Les événements ne sont gardés que pour le pas qui les a produits : qui a besoin du run entier
  (le rejeu) doit les garder lui-même.

## Alternatives envisagées
- **Un écouteur (observer) appelé par le moteur** : écarté pour l'instant. Une liste renvoyée est
  plus simple à tester, n'a pas d'ordre d'abonnement, et garde le run reproductible.
- **Garder les logs et les relire pour le score** : écarté. Les logs sont faits pour les gens, et
  leur texte peut changer.
- **Une couverture provisoire pour finir sur un seuil dès maintenant** : écarté. La couverture a
  besoin de la bande du sonar (ADR-0011) et appartient à #133.
