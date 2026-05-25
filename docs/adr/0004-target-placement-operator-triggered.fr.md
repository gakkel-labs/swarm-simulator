# ADR-0004 : Le placement de cible est déclenché par l'opérateur mais architecturalement extensible

## Status
Accepted — 2026-05-19

## Contexte
La recherche et secours (SAR) demande de placer une cible que l'essaim doit trouver par
exploration. Il faut un mécanisme de déclenchement. Pour le MVP, l'opérateur clique dans
Unity. Plus tard (v0.2), on voudra un placement aléatoire piloté par CLI et un chargement
de scénarios depuis fichier pour des benchmarks reproductibles et des tests headless.

## Décision
- RPC `PlaceTarget(position)` comme API externe (déclenchée par l'opérateur)
- `SimulationService.placeTarget(pos, TriggerSource)` comme point d'entrée interne
- Le handler RPC est un adapteur fin (validation + délégation, aucune logique)
- Enum `TriggerSource` avec uniquement `OPERATOR_CLICK` pour l'instant, extensible

## Conséquences
+ Ajouter des déclencheurs CLI / scénario plus tard = 2 lignes (nouvelle valeur d'enum +
  nouveau site d'appel)
+ Le backend reste autoritaire (position validée, horloge sim, `FoundEvent` émis côté serveur)
+ Un seul chemin de code pour tous les déclencheurs → comportement cohérent et tests
  plus simples
− L'enum avec une seule valeur ressemble à de la sur-ingénierie à la première lecture
  (atténué par cet ADR — les futurs lecteurs savent pourquoi)

## Alternatives considérées
- **Force d'attraction vers la cible** : rejetée, transforme « recherche » en « goto » —
  tue le comportement émergent qu'on veut démontrer.
- **Détection côté Unity (Unity décide quand la cible est trouvée)** : rejetée, casse
  l'autorité du backend et empêche les tests headless.
- **Position de cible codée en dur dans la config backend** : rejetée, aucune flexibilité
  pour la démo.
