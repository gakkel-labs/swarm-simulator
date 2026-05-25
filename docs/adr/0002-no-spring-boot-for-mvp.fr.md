# ADR-0002: No Spring Boot for the MVP backend

## Status
Accepted — 2026-01-15 (rétroactif, formalisé 2026-05-25)

## Context
Le backend `swarm-server` est un serveur Java qui héberge la boucle de simulation Boids et
expose un service gRPC. Il faut décider de son squelette d'application.

L'auteur connaît Spring Boot et le pratique sur d'autres projets pro. La tentation par défaut
serait d'utiliser le starter `spring-boot-starter` + `grpc-spring-boot-starter` parce que
c'est familier. Mais ce projet a des contraintes spécifiques :

- **MVP frugal** : v0.1 est un simulateur mono-processus, pas de base de données, pas
  d'authentification (cf. CLAUDE.md), un seul `port 50051` gRPC.
- **Démo lançable en 2 minutes** : objectif explicite du projet. `java -jar swarm-server.jar`
  doit suffire — pas de profil, pas de config Spring, pas d'attente de bean refresh.
- **Lecture pédagogique** : un dev qui découvre le repo doit pouvoir suivre le wiring linéairement.
  Spring cache le câblage derrière des annotations et de l'auto-config.
- **Poids et démarrage** : Spring Boot ajoute ~15 Mo et 2-3 s de cold start. Sur Raspberry Pi
  (v0.3), c'est non négligeable.
- **Composants à câbler** : un `World`, une `SimulationLoop` (ScheduledExecutorService 30 Hz),
  un broadcaster (ScheduledExecutorService 20 Hz), un `ServerBuilder` gRPC avec 3 services.
  Tout cela se fait en < 60 lignes dans un `main()`.

## Decision
- Pas de Spring Boot, pas de Micronaut, pas de Quarkus, **pas de framework d'injection** pour
  le MVP.
- Squelette = **`SwarmServer.main()`** (`swarm-server/.../server/SwarmServer.java`) qui
  délègue le wiring à **`SwarmServerBootstrap.create(port)`** — une classe statique de
  composition, lisible de haut en bas.
- Cycle de vie : `ServerBuilder.forPort(50051).build().start()` + `Runtime.addShutdownHook()`
  pour l'arrêt propre. Les `ScheduledExecutorService` sont créés avec une `ThreadFactory`
  qui nomme les threads (`sim-loop`, `swarm-broadcaster`) et les marque `daemon`.
- Si une dépendance externe non triviale apparaît plus tard (base de données, config
  externalisée, métriques Prometheus, profils dev/prod), on rediscutera l'introduction
  d'un framework — mais via un nouvel ADR qui supersede celui-ci.

## Consequences
+ **Démarrage instantané** : `main()` → serveur prêt en ~200 ms, idéal pour la démo et pour
  les tests d'intégration qui spinneront le serveur en-process.
+ **Wiring explicite et linéaire** : `SwarmServerBootstrap` se lit de haut en bas. Pas
  d'IoC magique, pas de cycle de bean à débugger.
+ **Empreinte mémoire et binaire minimale** : pertinent pour préparer l'agent Pi (v0.3).
+ **Tests faciles** : on peut instancier un `SwarmServerBootstrap` dans un test JUnit sans
  contexte Spring à charger, sans `@SpringBootTest` à attendre.
+ **Pédagogique** : un lecteur qui ouvre le repo voit immédiatement ce qui tourne et où.
− **Plus de code de plomberie à écrire si le scope grandit** : config externalisée,
  health endpoint riche, métriques, sécurité — il faudra les écrire ou les ajouter
  morceau par morceau.
− **Pas de profils Spring** : si on veut un mode `dev` vs `prod` plus tard, il faudra le
  bricoler à la main (env vars + petit parser d'args).
− **Risque de re-divergence** : sur les autres projets Gakkel (fleet-dashboard,
  drone-embedded), si Spring s'impose, le squelette du swarm-server tranchera. À assumer.

## Alternatives considered
- **Spring Boot + `grpc-spring-boot-starter`** : rejeté pour le MVP. Apporte une auto-config
  utile en prod (actuator, profils, externalised config) qui n'a aucune cible à servir ici,
  contre un coût en démarrage, lisibilité et poids.
- **Micronaut / Quarkus** : rejeté. Mêmes bénéfices que Spring mais sans la familiarité,
  et toujours surdimensionné pour un MVP mono-processus.
- **Framework DI léger (Guice, Dagger)** : rejeté. L'arbre de dépendances tient en une page,
  un constructeur statique est plus clair qu'un module Guice.
- **Aucune classe de bootstrap (tout dans `main`)** : rejeté. `SwarmServerBootstrap` permet
  d'injecter un `port` arbitraire et d'instancier le serveur en test sans dupliquer le `main`.
