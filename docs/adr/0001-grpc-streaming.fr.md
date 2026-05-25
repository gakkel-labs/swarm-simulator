# ADR-0001: Use gRPC server-streaming to push WorldState at 20 Hz

## Status
Accepted — 2026-01-15 (rétroactif, formalisé 2026-05-25)

## Context
Le client Unity doit afficher en temps réel l'état d'un essaim d'AUVs simulé côté backend Java :
positions et vélocités de ~50 agents, plus la cible, le prédateur et les obstacles. Le backend
est autorité (cf. ADR-0004), Unity n'est qu'un rendu.

Forces en jeu :
- **Push fréquent et continu** : la simulation tourne à 30 Hz côté serveur, on veut alimenter
  Unity à un rythme suffisant pour un mouvement fluide (≥ 20 Hz), sans saturer le réseau local.
- **Latence faible** : un essaim qui « saute » de 200 ms casse la perception d'émergence.
- **Schéma typé partagé** : le contrat doit être vérifié à la compilation pour limiter les
  bugs de désynchronisation Java ↔ C#.
- **Backend pluri-langage à terme** : le client Unity est en C#, l'agent Pi (v0.3) sera en
  Java, un éventuel dashboard pourrait être en TypeScript. On veut un même contrat partout.
- **Projet personnel, MVP frugal** : pas envie d'un broker (NATS, Kafka) ni d'un service mesh.

## Decision
- Transport unique : **gRPC** sur HTTP/2 cleartext (h2c) en local.
- Pattern : **server-streaming** unidirectionnel pour pousser `WorldState` à **20 Hz**
  (`SwarmObserver.SubscribeWorldState`).
- Pattern complémentaire : **RPC unaires** pour les commandes opérateur ponctuelles
  (`SimulationControl.PlaceTarget`, `PingService.SendPing`).
- Sérialisation : **Protocol Buffers proto3**, contrats centralisés dans le module
  Maven `contracts/` qui génère les stubs Java automatiquement (`protobuf-maven-plugin`).
  Côté C#, stubs générés via `Grpc.Tools` dans `unity-client/`.
- Découplage tick / broadcast : la boucle de simulation tourne à **30 Hz**, le broadcaster
  à **20 Hz** sur un `ScheduledExecutorService` séparé. Le client ne dicte pas le rythme.

## Consequences
+ Contrat fortement typé partagé Java/C# (et bientôt Java/Pi) avec génération automatique.
+ Streaming natif HTTP/2 : une seule connexion longue durée, pas de polling, latence ~ms en local.
+ Backpressure géré par gRPC (`ServerCallStreamObserver.isReady()`) — un client lent ne bloque
  pas le broadcaster.
+ Healthcheck (`Ping`) et reflection (`ProtoReflectionServiceV1`) intégrés gratuitement
  → on peut introspecter le serveur depuis `grpcurl` sans effort.
+ Évolutivité multi-clients : le broadcaster pousse à N subscribers via une `ConcurrentHashMap`,
  ouvre la voie à plusieurs vues simultanées (Unity + dashboard).
− Dépendance lourde : `grpc-netty-shaded` ajoute ~10 Mo au fat jar (acceptable hors embarqué).
− Côté Unity, gRPC.Core est deprecated ; on utilise `grpc-dotnet` + `YetAnotherHttpHandler`
  pour HTTP/2 — un peu de plomberie spécifique à l'environnement Unity.
− Threading Unity non-trivial : le stream arrive sur un thread .NET, il faut un dispatcher
  vers le main thread (cf. [notes-threading-grpc-unity.md](../notes-threading-grpc-unity.md)).

## Alternatives considered
- **REST + polling JSON** : rejeté. À 20 Hz, 50 polls/s avec overhead HTTP/1.1 et parsing JSON,
  pas de schéma typé, latence variable. Ne passe pas l'échelle multi-clients.
- **WebSocket + JSON ad hoc** : rejeté. Schéma non typé, pas de génération de stubs, on
  réinventerait la sérialisation et les versions de contrat.
- **MQTT / NATS / Kafka** : rejeté. Broker à déployer et opérer pour un projet personnel
  mono-machine. Surdimensionné pour un MVP frugal (cf. [ADR-0002](0002-no-spring-boot-for-mvp.md)).
- **UDP brut** : rejeté. Pas de schéma, pas de fiabilité, on rebâtirait un protocole.
  Aucun gain de latence perceptible en local.
- **gRPC bidirectional streaming** : rejeté pour le MVP. Les commandes opérateur sont rares
  (`PlaceTarget`), des RPC unaires séparés sont plus simples à raisonner.
