# Contribuer

[English](CONTRIBUTING.md)

Projet solo, écrit pour rester lisible et facile à faire évoluer.

---

## Lancer le projet en local

Voir [Essayer dans README.fr.md](README.fr.md#essayer) : démarrer le backend Java avec Maven,
ouvrir la vue 3D Unity, appuyer sur Play. Pas de base de données ni de service externe :
seul le port `50051` doit être libre.

---

## Conventions de code

**Java (backend)**
- Java 21 : records, sealed classes et pattern matching sont bienvenus.
- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) : indentation de 4 espaces, 100 colonnes maximum.
- Pas de Spring Boot pour l'instant : seulement `main()` + `ScheduledExecutorService` + `ServerBuilder`
  ([ADR-0002](docs/adr/0002-no-spring-boot-for-mvp.fr.md)). Spring Boot est prévu à l'étape 4.
- Les tests d'abord (TDD) : écrire un test qui échoue, puis le code minimal qui le fait passer, puis refactorer.
- Tests : JUnit 5 + Mockito, schéma AAA, noms de méthodes qui décrivent le cas.
- Les noms de variables doivent être explicites, sans identifiant d'une lettre ni abréviation.
- Les nouvelles classes portent les mots du [glossaire](docs/produit/glossaire.fr.md) (`Mission`,
  `Fleet`, `SearchArea`, `Contact`…).

**Protobuf** : définitions dans `contracts/`, proto3, champs en snake_case. Ne pas casser le
contrat de streaming `WorldState` sans changer la version du package. Le code C# de
`unity-client/Assets/Scripts/Grpc/Generated/` est généré par `contracts/generate-csharp.ps1` :
ne pas le modifier à la main.

**Docs** : chaque doc existe en anglais (`X.md`) et en français (`X.fr.md`), avec un lien vers
l'autre langue en haut. Une ADR acceptée ne se modifie pas : on écrit une nouvelle ADR
([modèle](docs/adr/TEMPLATE.fr.md)).

**Commits** : [Conventional Commits](https://www.conventionalcommits.org/), à l'impératif, en anglais :
```
feat(boids): add lateral avoidance when obstacles are flanked
fix(grpc): close stream cleanly on server shutdown
docs(readme): add Unity setup screenshots
```
Scopes valides : `boids`, `domain`, `agent`, `server`, `grpc`, `proto`, `contracts`, `unity`, `ci`,
`docs`. Pour la doc, le scope peut nommer la partie : `docs(readme)`, `docs(adr)`, `docs(produit)`.

---

## Pull requests

1. La CI doit être verte (build Maven + tests JUnit) avant le merge.
2. Préférer des PR petites et ciblées : un sujet par PR.
3. Citer l'issue dans la description (`Closes #N`).
4. On ne merge que du travail prêt. Une PR en brouillon sert à montrer tôt.

---

## Signaler un problème

Les issues sont écrites en anglais simple. Indiquer : ce qui était attendu, ce qui s'est passé
(stack trace / log / erreur de la Console Unity), les étapes pour reproduire (version du JDK,
version d'Unity, OS), et un label (`bug`, `enhancement` ou `question`).

Pour les questions de conception ou d'architecture, ouvrir plutôt une Discussion.
