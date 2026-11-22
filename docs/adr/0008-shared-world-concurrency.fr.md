# ADR-0008 : Partager le World entre les threads sans verrou

[English](0008-shared-world-concurrency.md)

## Status
Proposed
Date : 2026-11-22

## Contexte
Trois sortes de threads touchent le même `World` (voir l'[architecture](../architecture.fr.md)) :

- **`sim-loop`** (30 Hz) déplace les drones et le danger. Il retire aussi un drone quand le
  danger l'attrape, et le remet 5 s plus tard.
- **`swarm-broadcaster`** (20 Hz) lit tout le monde pour construire un `WorldState`.
- **Les threads gRPC** placent la cible (`PlaceTarget`).

Avant #79, les listes de drones et d'obstacles étaient de simples `ArrayList`. Le diffuseur
pouvait lire la liste pendant que la simulation retirait un drone : cela peut lancer une
`ConcurrentModificationException` ou montrer une liste à moitié modifiée. La doc disait que ces
listes étaient « immuables », ce qui était faux.

On veut une correction simple à lire (c'est un projet d'apprentissage), et assez rapide pour
quelques dizaines de drones à 30 Hz.

## Décision
Pas de `synchronized` et pas de verrou. Chaque morceau d'état partagé utilise l'outil le plus
léger qui le rend sûr :

| État | Outil | Pourquoi |
|---|---|---|
| `World.agents`, `World.obstacles` | `CopyOnWriteArrayList` | Beaucoup de lectures (à chaque tick, à chaque diffusion), peu d'écritures (un drone attrapé ou remis). Un lecteur parcourt toujours une copie stable. |
| `World.predator`, `World.target` | champ `volatile` | Remplacés d'un bloc ; les lecteurs voient la référence la plus récente. |
| Position et vitesse d'`Agent` / `Predator` | un seul `volatile Motion` (record immuable qui porte les deux vecteurs) | Un lecteur ne voit jamais un vecteur à moitié écrit, et la position et la vitesse viennent toujours du même tick. |
| Détection de la `Target` | `AtomicReference` | Seul le premier drone qui détecte la cible gagne (compare-and-set). |
| Résultats de `CohesionMetric`, `FlockingDiagnostician` | champ `volatile` | Écrits par `sim-loop`, lus par le diffuseur. |

Dans un tick, `SimulationLoop` prend d'abord une copie de la liste des drones (`List.copyOf`),
calcule toutes les forces à partir de cette copie, puis déplace les drones.

Ce que cela garantit :
- **Cohérence par élément** : chaque drone est soit dans la liste, soit absent, et chaque vecteur
  est entier.
- **Pas de cohérence par photo** : un `WorldState` peut mélanger des drones du tick N et du
  tick N+1. Pour un même drone, la position et la vitesse vont toujours ensemble, car elles sont
  lues d'un seul `Motion`. À 20 Hz à l'écran, le mélange entre drones ne se voit pas.

## Conséquences
+ Plus de plantage quand des drones partent et reviennent pendant une diffusion.
+ Facile à lire : pas d'ordre de verrous, pas de risque d'interblocage.
+ Léger à notre taille : une copie de 20 références quand un drone est attrapé.
− Un `WorldState` n'est pas un instant exact. Ce qui a besoin d'un instant exact (une image de
  rejeu, un score) ne doit pas s'appuyer dessus.
− La copie à l'écriture devient lente si la liste change souvent (par exemple des centaines de
  drones déposés un par un à chaque tick).

Cette décision doit être **revue à l'étape 2**. L'optimiseur a besoin d'exécutions rejouables
avec une horloge de simulation (`step(dt)`), donc d'un état propre à chaque tick. La suite
probable : la boucle construit une photo immuable du monde à la fin de chaque tick et la publie
par une seule écriture `volatile` (ou `AtomicReference`) ; les lecteurs n'utilisent que cette photo.

## Alternatives considérées
- **`synchronized` ou un `ReadWriteLock` autour du monde** : rejetée pour l'instant. Cela donne
  la cohérence par photo, mais le diffuseur bloquerait la simulation (ou l'inverse), et l'ordre
  des verrous devient une chose de plus à bien faire.
- **Une photo immuable publiée à chaque tick** : la meilleure cible, mais elle change l'écriture
  de `SimulationLoop` et de `World`. Gardée pour l'étape 2.
- **Garder `ArrayList` et attraper l'exception** : rejetée. Cela cache la course au lieu de la supprimer.
