# ADR-0010 : Garder les drones dans une liste triée par id, et ne pas optimiser pour l'instant

[English](0010-sorted-drone-list.md)

## Status
Proposed
Date : 2026-11-24

## Contexte
L'issue #80 demande que deux lancements avec la même graine donnent exactement le même résultat,
au bit près. L'optimiseur de flotte (#83) lancera 300 à 3 000 simulations par configuration de
flotte et les comparera : une source de hasard cachée rendrait la comparaison injuste.

Un test a montré que **l'ordre des drones** change le résultat. Avec les mêmes drones ajoutés
dans l'ordre inverse, les positions différaient sur les derniers chiffres après 10 s
(`10.318754153179452` contre `10.31875415317946`). La raison : Boids additionne les positions
des voisins, et chaque addition de nombres à virgule est arrondie, donc `(a + b) + c` n'est pas
toujours égal à `a + (b + c)`. L'écart grandit ensuite à chaque pas.

Jusqu'ici, `World` gardait ses drones dans une `CopyOnWriteArrayList`, dans l'ordre où ils
avaient été ajoutés. Un drone mangé par le danger revenait en fin de liste : l'ordre changeait
donc pendant une simulation.

`World` est lu par plusieurs threads (ADR-0008) : le thread de simulation (30 Hz) et celui qui
envoie le `WorldState` à Unity (20 Hz). Les drones ne sont ajoutés ou retirés que rarement : à la
création du monde, puis quand un drone est perdu ou revient.

## Décision
- `World` garde sa `CopyOnWriteArrayList`, mais `addAgent` insère chaque drone **à sa place selon
  son id**. Toutes les boucles sur les drones (forces Boids, danger, détection de la cible)
  tournent alors dans le même ordre à chaque lancement.
- Les ids des drones et du danger sont tirés de la graine de la simulation : « trié par id » donne
  donc le même ordre à chaque lancement avec cette graine.
- **Pas d'optimisation pour l'instant.** L'insertion est une simple boucle sur la liste, et la
  recherche des voisins compare encore tous les drones deux à deux. Mesuré le 2026-11-24 :
  10 minutes simulées prennent environ 0,23 s avec 20 drones et 3,2 s avec 100 drones. C'est
  assez rapide pour l'étape 2. L'optimisation attendra d'être nécessaire (#166 : grille spatiale,
  quand les flottes dépasseront une cinquantaine de drones).

## Conséquences
- Même graine, même résultat : le test `SimulationTest.Reproducibility` le vérifie à chaque build.
- Les lecteurs voient toujours une copie figée de la liste, comme décidé dans l'ADR-0008 : pas
  besoin de verrou.
- Chaque ajout ou retrait recopie toute la liste et cherche la place drone par drone. Ça ne coûte
  rien avec 20 drones et des changements rares, mais ça compterait avec des milliers de drones
  qui changent souvent.
- `World.agents()` renvoie toujours une `List` : aucun appelant n'a dû changer.

## Alternatives envisagées
- **Une collection triée (`TreeSet`, `TreeMap`)** : elle trie toute seule, mais elle n'est pas
  sûre quand un thread la modifie pendant qu'un autre la lit. Rejetée, car le thread de Unity lit
  le monde.
- **Une collection triée et concurrente (`ConcurrentSkipListSet`,
  `ConcurrentSkipListMap<UUID, Agent>`)** : elle trie toute seule et elle est sûre entre threads,
  mais un lecteur peut la voir au milieu d'un changement (pas une copie figée), elle n'a pas
  d'accès par index, et il faudrait changer tous les appelants de `World.agents()`. Inutile pour
  une liste qui change si rarement.
- **Trier une copie à chaque pas (`List.copyOf(...)` puis `sort`)** : simple, mais on trierait
  30 fois par seconde une liste qui ne change presque jamais, et on oublierait les autres boucles
  (danger, diagnostics) qui lisent le monde directement.
- **Optimiser tout de suite (recherche dichotomique, grille spatiale)** : rejeté pour l'instant.
  Le code reste simple tant que les temps mesurés sont bas. Gardé pour plus tard dans #166.
