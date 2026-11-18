# Gakkel — Swarm Simulator

## Contexte
Gakkel est un système de missions de drones sous-marins. Projet personnel, dépôt public.
Mission de référence : la **recherche civile d'une épave** (avion ou navire) sur le fond marin.
Autres missions prévues plus tard : inspection, cartographie du fond (bathymétrie), guerre des mines,
exploration scientifique (prélever des échantillons, poser ou récupérer des instruments, mesurer l'eau).
L'origine du projet est l'algorithme **Boids** (comportement d'essaim) : il doit rester visible.

Vocabulaire : toujours utiliser les mots du glossaire, dans la doc, les issues et le code.
Voir `docs/produit/glossaire.fr.md` (FR) et `docs/produit/glossaire.md` (EN).
- **Opérateur** : la personne qui prépare et suit la mission depuis le centre d'opérations.
  Dans les docs publiques, dire « l'opérateur » (pas de prénom).
- **Flotte** = un **porte-drones** + un **essaim** de **drones de détection** + un ou plusieurs **drones spécialisés**.
- Étapes d'une flotte : **Déposer → Détecter → Confirmer → Intervenir**
  (Drop → Detect → Confirm → Intervene).
- **Zone de recherche** (search area), jamais « zone » tout court. Toujours « drone », jamais « véhicule ».
- Persona : `docs/produit/persona-operateur.fr.md`.

## Rôle de chaque partie
- **Poste de contrôle (web, dépôt `mission-console`)** : l'opérateur définit la mission
  (objectif, zone de recherche sur carte marine, météo, AIS), reçoit et valide la configuration de flotte proposée.
  La simulation est une **aide, pas une obligation** : l'opérateur peut aussi créer une mission seul, de bout en bout,
  en choisissant lui-même la configuration de flotte, sans passer par la simulation.
  Le framework web n'est **pas encore choisi** : n'en nommer aucun.
- **Backend Java (ce dépôt, `swarm-server/`)** : lance beaucoup de simulations, propose la meilleure
  configuration (types et nombre de drones, réglages), génère les fichiers de mission, exécute la mission.
- **Unity (`unity-client/`)** : affichage seulement (suivre une mission en direct, la rejouer). Aucune décision.

## Stack
- Java 21, Maven multi-modules, grpc-java, JUnit 5 + Mockito
- gRPC streaming serveur (proto3) pour pousser le `WorldState` au client Unity
- Unity 6 (6000.4) + URP, client gRPC C# avec `Grpc.Net.Client`

## Modules
- `contracts/` : définitions protobuf (`src/main/proto/`) + `generate-csharp.ps1` pour le code C#
- `swarm-server/` : serveur gRPC + simulation Boids (package `fr.gakkel.swarmsimulator.swarmserver`)
- `tests/` : tests d'intégration (module encore vide)
- `unity-client/` : projet Unity
- `docs/` : architecture, ADR (`docs/adr/`), produit (`docs/produit/`)

## Commandes
```
mvn -pl swarm-server -am package                 # build
mvn -pl swarm-server exec:java -Dexec.mainClass="fr.gakkel.swarmsimulator.swarmserver.server.SwarmServer"   # lancer le serveur (port 50051)
mvn -pl swarm-server test                        # tests du serveur
mvn verify                                       # tout le build + tests
```
Variables utiles : `SWARM_PORT`, `SWARM_SEED`, `SWARM_AGENT_COUNT`.

## Conventions de code
- Java 21 : records, sealed classes, pattern matching bienvenus
- Style : Google Java Style Guide (4 espaces, 100 colonnes), noms de variables explicites
- Pas de Spring Boot pour l'instant (`main()` + `ScheduledExecutorService` + `ServerBuilder`, voir ADR 0002).
  Spring Boot est prévu à l'étape 4 : ne pas l'ajouter avant.
- **TDD** : écrire d'abord un test qui échoue, puis le code minimal qui le fait passer, puis refactorer
  (JUnit 5 + Mockito, schéma AAA).
- **DDD, au fil de l'eau** : les nouvelles classes portent les noms du glossaire (`Mission`, `Fleet`,
  `DroneCarrier`, `Contact`, `SearchArea`…). On renomme l'existant quand on y touche. Pas de classes vides à l'avance.
- Protobuf : proto3, champs en snake_case ; ne pas casser le contrat `WorldState` sans changer la version du package
- Commits : Conventional Commits, à l'impératif. Scopes : voir `CONTRIBUTING.md`

## Ne pas toucher
- Le code C# généré : `unity-client/Assets/Scripts/Grpc/Generated/` (le régénérer avec `contracts/generate-csharp.ps1`)
- `LICENSE` (PolyForm Noncommercial 1.0.0)
- Les ADR déjà acceptées : en écrire une nouvelle plutôt que modifier l'ancienne
- `.claude/settings.local.json` (local, non versionné)

## Règles du projet
- **Docs toujours en double** : `X.md` (anglais) + `X.fr.md` (français), avec un lien de l'une vers l'autre en haut
- **Issues GitHub en anglais simple** uniquement (titres, textes, commentaires, milestones, labels)
- **Aucune mention d'IA** dans ce qui est poussé : pas de « Generated with Claude Code »,
  pas de « Co-Authored-By: Claude », pas de lien de session, ni dans les commits, ni dans les PR, ni dans les issues
- Ne jamais parler de recrutement, de candidature ni d'entreprise cible
- Ne pas parler de coût des drones (« low-cost », « plus cher ») pour l'instant
- Quand une issue passe « In progress » : écrire en haut sa user story
  (« As an operator, I want… so that… ») et ses critères d'acceptation

## Feuille de route
6 étapes (milestones « Step 1 » à « Step 6 » sur GitHub). Étape 1 : une belle scène Unity
(drone 3D, fond marin) pour montrer le projet. Les idées pour plus tard sont dans l'issue #120.
Budget : environ 6 h par semaine.

## Workflow Claude Code
- Plan Mode obligatoire pour une tâche de 3 étapes ou plus, ou une décision d'architecture
- Subagent Explore (intégré) pour explorer le dépôt ; agent `boids-explorer` pour la simulation
- Skills dans `.claude/skills/` pour les conventions répétables

## Style de réponse souhaité
- **Projet d'apprentissage** : je développe ce projet pour apprendre (DDD, TDD, Unity, domaine maritime).
  Explique brièvement chaque choix et les notions nouvelles. Si je demande à écrire le code moi-même,
  guide-moi au lieu de l'écrire.
- Quand tu utilises un concept de DDD (entité, objet-valeur, agrégat, bounded context…), explique-le
  en une ou deux phrases simples, avec un exemple tiré de Gakkel.
- Réponses en français, concises et pragmatiques
- Explique le « pourquoi » des choix techniques
- Propose un message de commit conventional à la fin
