# ADR-0003: Minimal 3D assets — grey capsules, primitive shapes, no art pipeline

## Status
Accepted — 2026-01-15 (rétroactif, formalisé 2026-05-25)

## Context
Le client Unity affiche un essaim d'AUVs, une cible SAR, un prédateur et des obstacles dans
une scène 3D. La question esthétique se pose dès qu'on ouvre Unity : doit-on modéliser de
vrais AUVs (sous-marins texturés, FX d'eau, post-processing, skybox marine) ou rester sur
des primitives ?

Forces en jeu :
- **Le sujet du projet est l'émergence comportementale**, pas le rendu sous-marin. La valeur
  démontrable est la cohésion d'essaim, l'évitement de prédateur, le pattern de recherche
  SAR — pas la qualité d'un shader.
- **Le backend est l'autorité** (cf. ADR-0001/ADR-0004). Le client est jetable, remplaçable
  par un dashboard 2D demain sans rien casser. Investir dans des assets coûteux côté Unity
  serait contradictoire avec cette frontière.
- **Projet personnel solo**, pas de game designer ni d'artiste 3D. Chaque heure passée à
  texturer est une heure non passée sur l'algo Boids, l'agent Pi (v0.3) ou l'ACO (v0.5).
- **Démo lançable en 2 minutes** (CLAUDE.md). Un projet Unity avec des assets lourds
  augmente le temps de Play, le poids du repo, et la friction d'install.
- **Lisibilité du comportement** : avec 50 capsules grises identiques, l'œil identifie
  immédiatement la dynamique de groupe. Avec 50 modèles détaillés différents, le pattern
  visuel se brouille.
- **Cohérence Gakkel** : les projets sœurs (`gakkel-fleet-dashboard`, `gakkel-drone-embedded`)
  visent la lisibilité opérationnelle, pas le photoréalisme.

## Decision
- **Agents** : `GameObject` `Capsule` primitif Unity, matériau gris uni (Lit URP, couleur
  unique). Pas de texture, pas d'animation, pas de variation par `AgentType` au-delà
  d'une éventuelle teinte plus tard.
- **Cible SAR** : `Sphere` primitive, matériau qui passe au vert quand la cible est trouvée
  (`SearchStatus.found_event`).
- **Prédateur** : primitive distincte (forme + couleur contrastée) — un volume facilement
  identifiable, pas une modélisation de menace réaliste.
- **Obstacles** : primitives géométriques (`Cube` / `Cylinder`), matériau neutre.
- **Environnement** : skybox par défaut Unity, pas d'eau, pas de post-processing volumique,
  pas de FX. URP standard, pipeline non customisé.
- **Caméra** : `CameraController.cs` basique (orbit / pan / zoom), pas de cinematic.
- **HUD** : `SimulationUI.cs` texte simple (compteurs, timer). Pas de motion design.

## Consequences
+ **Focus visuel sur l'émergence** : un observateur voit immédiatement la cohésion,
  l'alignement, la séparation, la fuite face au prédateur. L'œil n'est pas distrait.
+ **Itération rapide** : on peut tester un changement Boids sans repasser par Unity Editor
  pour ré-importer un FBX ou re-baker un lightmap.
+ **Repo léger** : pas de gros binaires, pas de pression sur Git LFS, clonage rapide.
+ **Démo reproductible partout** : URP standard tourne sur n'importe quelle machine
  récente sans GPU dédié.
+ **Coût de remplacement nul** : si un jour on veut un vrai AUV (post-v0.6, ou démo
  client), il suffit de swap le mesh du prefab `Agent` — aucun script ne dépend de la forme.
− **Démo pas « impressionnante » au premier regard** : le viewer croisé qui s'attend
  à un AAA aquatique sera déçu. Le pitch doit assumer le parti pris (« focus comportement »).
− **Si le projet pivote vers un produit grand public ou un livrable client**, l'investissement
  visuel sera à faire à ce moment-là — pas avant.
− **Identification visuelle par type d'agent (v0.4)** : il faudra introduire au minimum
  3 couleurs pour distinguer EXPLORER/OPERATOR/CARRIER. Trivial, pas bloquant.

## Alternatives considered
- **Modèles AUV réalistes (FBX texturé + animation)** : rejeté. Coût horaire élevé, valeur
  pédagogique nulle, brouille la lecture de l'émergence.
- **Asset store pack « sous-marin »** : rejeté. Dépendance externe, licence à gérer,
  visuel souvent générique, et toujours un coût de Play et de poids.
- **Shader d'eau + post-processing volumique** : rejeté. Joli mais coûte des FPS et masque
  les agents au fond. Hors scope MVP.
- **Couleurs distinctes par agent dès le départ** : rejeté pour v0.1. On ne différencie pas
  encore les types (`AgentType` existe dans le proto mais n'est pas exploité visuellement
  avant v0.4). Garder gris uniforme renforce la lecture « essaim ».
- **Rendu 2D top-down** : rejeté. La simulation est 3D (axe vertical utile pour le pattern
  de recherche en profondeur), un rendu 2D perdrait cette dimension.
