# ADR-0009 : Une seule base PostgreSQL tenue par le backend, et une installation qui marche hors ligne

[English](0009-storage-and-offline-operation.md)

## Status
Proposed
Date : 2026-11-23

## Contexte
À l'étape 2, le backend lance des centaines de simulations pour calculer le score d'une
configuration de flotte (probabilité de réussite de la mission, voir la
[fiche problème](../produit/problem-find-a-wreck.fr.md)). Aujourd'hui rien n'est gardé :
quand le processus s'arrête, les résultats sont perdus. Il faut les garder, pour quatre raisons :

1. **Le poste de contrôle web** : des centaines de simulations prennent du temps. Le poste de
   contrôle lance le calcul, puis lit le résultat quand il est prêt.
2. **Ne pas recalculer** : même mission + même configuration de flotte + mêmes scénarios +
   même version du code = même résultat. On peut le réutiliser.
3. **Comparer dans le temps** : par exemple, vérifier qu'un schéma de recherche s'améliore
   d'une version à l'autre.
4. **Les campagnes** : un futur planificateur de campagne aura besoin des résultats des
   missions précédentes.

Gakkel s'utilise à bord d'un navire. En mer, la liaison internet (satellite) est lente, chère
ou coupée. Tout le système doit marcher **sans internet**, sur un poste à bord.

L'[ADR-0002](0002-no-spring-boot-for-mvp.fr.md) dit qu'une base de données est une raison de
reconsidérer un framework. Cette ADR ajoute une base de données, mais pas de framework.

## Décision
**Une seule base, tenue par le backend.**
- **PostgreSQL**, avec l'extension **PostGIS** (les zones de recherche, les positions et, plus
  tard, les cartes marines S-57 sont des données géographiques).
- Seul le backend Java la lit et l'écrit. Le poste de contrôle web n'a jamais sa propre base :
  il passe toujours par l'API du backend.

**Ce qu'on stocke : des résumés, pas des trajectoires.**
- Une exécution de simulation : la mission, la configuration de flotte (schéma de recherche
  compris), le jeu de scénarios commun (une seed par scénario), la version du code, l'heure de
  lancement, et qui l'a lancée (vide pour l'instant ; rempli quand le poste de contrôle aura
  des comptes utilisateurs).
- Un résultat par scénario : réussite ou échec, heure de fin, drones perdus (détection,
  spécialisés, porte-drones).
- Le score : probabilité de réussite, temps estimé (le temps sous lequel 9 scénarios réussis
  sur 10 se terminent) et temps moyen.
- Les trajectoires complètes (pour le rejeu) ne sont **pas** stockées maintenant. Elles auront
  leur propre décision avec la fonction de rejeu.

**Derrière un port (DDD).**
- Le domaine définit une interface, `SimulationResultRepository`. Elle parle avec les mots du
  glossaire et ne connaît rien au SQL.
- Un adaptateur PostgreSQL l'implémente en JDBC simple. Les changements de schéma sont des
  scripts SQL versionnés (Flyway).
- Les tests du domaine utilisent un faux en mémoire du port. Les tests de l'adaptateur
  utilisent un vrai PostgreSQL lancé par **Testcontainers**.

**Aucun secret dans le dépôt.**
- L'utilisateur et le mot de passe de la base viennent de variables d'environnement, jamais du code.
- Un fichier `.env` les contient sur chaque machine et n'est jamais commité (il est dans
  `.gitignore`). Un fichier `.env.example`, avec de fausses valeurs, est commité pour montrer
  quelles variables sont nécessaires.

**Seulement des outils gratuits.**
- Tous les outils de cette décision sont gratuits et open source : PostgreSQL, PostGIS, Flyway
  (édition Community), Testcontainers, Docker Engine et Docker Compose.
- Sous Windows, Docker Desktop est gratuit pour un usage personnel et non commercial (une
  licence payante est demandée seulement aux grandes entreprises). Sur un poste à bord sous
  Linux, Docker Engine suffit et reste toujours gratuit.

**Hors ligne sur un poste à bord.**
- Tout le système (base, backend, poste de contrôle web) démarre avec un seul fichier
  **Docker Compose**, sur une machine, sans internet.
- Aucune dépendance en ligne pendant l'utilisation : pas de CDN (scripts, styles et polices
  sont livrés avec le poste de contrôle), pas de fonds de carte en ligne (les cartes S-57 sont
  chargées depuis des fichiers locaux), pas de service cloud.
- Les données qui viennent d'habitude d'internet viennent du navire, ou sont chargées avant le
  départ : l'AIS vient du récepteur du navire, la météo d'un fichier chargé avant le départ.
- Internet sert seulement à **installer ou mettre à jour** le poste (télécharger les images),
  pas à mener une mission.

## Conséquences
+ Les résultats survivent à un redémarrage, se comparent dans le temps et se réutilisent.
+ Une seule source de vérité : pas de synchronisation entre deux bases.
+ Le domaine ne dépend pas de PostgreSQL : le stockage peut changer sans toucher à la simulation.
+ Marche en mer sans internet, ce qui est une vraie contrainte pour les opérateurs.
+ PostgreSQL + PostGIS est un choix courant dans l'industrie, et il portera les cartes S-57 plus tard.
− Lancer le backend demande maintenant une base : Docker devient un outil nécessaire à chaque
  développeur.
− Les tests de l'adaptateur sont plus lents (un conteneur démarre) : ils restent peu nombreux ;
  la plupart des tests utilisent le faux en mémoire.
− « Hors ligne seulement » exclut des services en ligne pratiques (fonds de carte, base
  hébergée, CDN).
− La version du code doit faire partie de la clé de réutilisation, sinon un vieux résultat
  pourrait être réutilisé après un changement du code.

## Alternatives considérées
- **D'abord des fichiers JSON, une base plus tard** : plus simple au tout début, mais on
  écrirait le stockage deux fois. Avec le port en place, partir sur PostgreSQL coûte peu de plus.
- **Une base pour le web, une pour le backend** : rejetée. Deux vérités à synchroniser, et le
  web n'a rien à stocker que le backend ne possède pas déjà.
- **SQLite ou H2 (base embarquée)** : plus simple à installer, mais faible pour les données
  géographiques et pour plusieurs utilisateurs en même temps. Pas le standard de l'industrie
  pour ce genre de système.
- **Une base dans le cloud** : rejetée, elle ne marche pas sans internet, et elle n'est pas gratuite.
- **Spring Data / JPA** : rejeté pour l'instant (l'ADR-0002 reste valable). Le JDBC simple
  garde le câblage visible et suffit pour quelques tables.
