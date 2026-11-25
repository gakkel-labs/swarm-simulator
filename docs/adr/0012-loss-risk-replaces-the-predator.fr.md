# ADR-0012 : Perdre des drones par un risque de perte, et garder le prédateur pour la démo seulement

[English](0012-loss-risk-replaces-the-predator.md)

## Status
Proposed
Date : 2026-11-25

## Contexte
Depuis l'étape 1, l'essaim fuit un **prédateur** qui nage vers le drone le plus proche et le
« mange ». Le glossaire l'appelait « danger » (un navire, un filet, un animal, une zone
interdite). L'issue #130 prévoyait d'abord de le renommer et d'ajouter des niveaux de danger
(nombre de prédateurs, vitesse, rayon de capture).

Avant de coder, nous avons lu les études publiées sur les pertes de drones sous-marins :

- Un drone n'est presque jamais perdu parce que quelque chose le chasse. Il est perdu par des
  pannes ordinaires (fuite, batterie, navigation, implosion en profondeur), plus graves là où
  on ne peut pas le récupérer (glace, grande profondeur, mauvais temps) ([Brito, Griffiths et Challenor, 2010](https://nora.nerc.ac.uk/id/eprint/169592/)).
- Le risque grandit avec le temps ou la distance de plongée. La même panne a environ 5 fois
  plus de chances de faire perdre le drone sous la banquise, et 9 fois sous une plateforme de
  glace, qu'en eau libre.
- En eaux côtières, deux drones REMUS-100 ont fait 189 missions sans perte ; les experts
  estiment 0,3 % à 1,9 % par mission de 14 km ([Griffiths et al., 2009](https://nora.nerc.ac.uk/id/eprint/169184/)).
- Une étude polaire donne 0,18 % pour une mission de 24 h par les pannes internes seules, et
  4,4 %, 13,2 % et 35 % en bonnes, moyennes et mauvaises conditions polaires ([JMSE, 2023](https://www.mdpi.com/2077-1312/11/10/1976)).

Un prédateur avec une vitesse et un rayon de capture ne modélise pas cela. Mais il rend bien
dans la vue en direct, et il explique pourquoi un drone disparaît.

## Décision
1. Le **risque de perte** (`LossRiskLevel`) est la notion de mission. L'opérateur choisit un
   niveau pour la zone de recherche. Chaque niveau donne la probabilité de perdre un drone en
   24 h de plongée :

   | Niveau | Perte en 24 h de plongée | Situation type |
   |---|---|---|
   | `NONE` | 0 % | conditions idéales, démo en direct |
   | `LOW` | 0,2 % | pannes internes seules, drone mûr |
   | `MEDIUM` | 2 % | eaux côtières |
   | `HIGH` | 5 % | côtes rocheuses, trafic dense ; environ bonnes conditions polaires |
   | `EXTREME` | 15 % | environ conditions polaires moyennes |

   La valeur des mauvaises conditions polaires (35 %) est écartée : on ne lancerait pas une
   recherche d'épave civile à ce niveau de risque.
2. Le taux est constant : `p(pas) = 1 - exp(-taux x dt)`, avec le taux tiré de la valeur sur
   24 h. À chaque pas, la simulation tire avec la graine, drone par drone dans l'ordre des id,
   si le drone est perdu. Un drone perdu quitte le monde, ne revient jamais, et apparaît dans
   `lostDroneIds()`. Avec `NONE`, il n'y a aucun tirage : les exécutions sont exactement les
   mêmes qu'avant.
3. Le **prédateur** reste, pour la **démo en direct seulement**. Il est activé par défaut dans
   la vue en direct (`SWARM_PREDATOR_ENABLED=true`), avec le risque de perte `NONE`, pour qu'aucun
   drone ne disparaisse sans raison visible. `Simulation.create` sans prédateur n'en a pas : les
   lots de simulations n'en ont jamais.
4. La « zone de danger » (un lieu où le risque de perte est plus fort) n'est pas ajoutée
   maintenant. Elle est étudiée dans #190.

## Conséquences
- L'optimiseur (#83) et le fichier de mission (#161) peuvent utiliser une seule entrée simple :
  le niveau de risque de perte.
- Les drones perdus deviennent un résultat d'exécution, comme décidé pour #83 (un drone perdu
  reste perdu).
- Les valeurs sont des ordres de grandeur, surtout tirés d'études polaires et d'avis d'experts
  sur un seul drone. Elles sont à un seul endroit (`LossRiskLevel`) et peuvent changer.
- Un seul niveau pour toute la mission est une simplification : le vrai risque dépend de la
  phase de la mission (la mise à l'eau et la récupération sont les plus risquées, voir #178),
  de la profondeur et de la météo.
- Le prédateur de démo utilise toujours la règle Boids de fuite : Boids reste visible dans la
  vue en direct.

## Alternatives envisagées
- **Renommer le prédateur en « danger » et le régler par niveau** (nombre, vitesse, rayon de
  capture) : pas réaliste, et un même mot aurait deux sens (un animal et un risque).
- **Une probabilité de perte par mission, tirée une seule fois** : plus simple, mais ignore la
  durée de plongée de chaque drone, et les schémas de recherche ne plongent pas tous aussi
  longtemps.
- **Prendre 35 % (mauvaises conditions polaires) pour `EXTREME`** : trop haut pour une
  recherche civile ; les responsables acceptent 10 % à 23 % pour toute une campagne polaire.
