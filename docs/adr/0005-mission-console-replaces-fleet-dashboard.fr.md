# ADR-0005: mission-console remplace fleet-dashboard

> English version: [`0005-mission-console-replaces-fleet-dashboard.md`](0005-mission-console-replaces-fleet-dashboard.md)

## Status
Accepted — 2026-11-16

## Context
Le projet frère `fleet-dashboard` était prévu comme un écran Java/Vaadin pour surveiller le
système en temps réel (détections envoyées par le Raspberry Pi, statut embarqué). Il n'est
jamais allé plus loin qu'un README.

La roadmap demande maintenant un vrai poste opérateur web : préparer une mission sur une
carte marine S-57, recevoir et modifier les flottes recommandées par le serveur, puis suivre
la mission en cours et envoyer des commandes. La surveillance en temps réel n'en est qu'une
partie.

## Decision
- Le nouveau dépôt [`gakkel-labs/mission-console`](https://github.com/gakkel-labs/mission-console)
  porte le poste opérateur web. `fleet-dashboard` est archivé.
- Le serveur Java (`swarm-simulator`) garde tout le calcul ; mission-console ne fait
  qu'afficher et envoyer les saisies de l'opérateur (REST + WebSocket). Unity reste la vue 3D.
- Le framework du front (Angular, React ou Vaadin) n'est pas encore choisi : c'est
  [mission-console#1](https://github.com/gakkel-labs/mission-console/issues/1), au début de
  l'étape 4 de la roadmap, noté dans son propre ADR.

## Consequences
- Un seul projet web au lieu de deux : périmètre plus clair, pas d'écran de suivi en double.
- Les ADR précédents qui citent `fleet-dashboard` (0002, 0003) gardent leurs décisions ; ils
  portent une note qui renvoie ici.

## Alternatives considered
- **Garder le nom `fleet-dashboard`** : rejeté, le poste parle de missions, pas seulement de
  la flotte.
- **Garder les deux projets** : rejeté, l'écran de suivi existerait en double.
