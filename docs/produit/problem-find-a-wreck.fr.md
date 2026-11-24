# Fiche problème : trouver une épave

> English version: [`problem-find-a-wreck.md`](problem-find-a-wreck.md)

## 1. Le problème

L'opérateur prépare une mission de recherche d'une épave coulée. Il ne sait pas quelle configuration de flotte choisir (nombre de drones de détection, drones spécialisés, schéma de recherche), ni si l'épave se trouve vraiment dans la zone de recherche. Gakkel l'aide à choisir la configuration qui a le plus de chances de réussir, sans mettre en danger les drones les plus importants.

Cette fiche parle d'une seule mission. Plus tard, l'opérateur pourra regrouper plusieurs missions dans une **campagne** (une suite de missions, voir le [glossaire](glossaire.fr.md)).

## 2. Causes supposées

Ce sont des hypothèses, à vérifier avec de vrais opérateurs.

- La position de l'épave n'est connue qu'à peu près (dernière position connue, dérive) : la zone de recherche est grande et ne contient peut-être pas l'épave.
- Il y a trop de configurations de flotte possibles pour les comparer à la main avant la mission.
- Chaque choix est un compromis entre sûreté, rapidité et usure de la flotte, et aujourd'hui rien ne le mesure avant la mission.

## 3. Contraintes

- La mission doit finir avant le **temps limite** (énergie des drones, météo).
- Le porte-drones ne doit jamais être perdu ; les drones spécialisés non plus, par défaut.
- Pour démontrer l'absence, la couverture doit atteindre le **seuil de couverture** (95 % par défaut) et chaque contact doit être classifié.
- Le résultat doit être reproductible : chaque configuration est comparée sur les mêmes scénarios.
- Tout doit marcher **sans internet**, sur un poste à bord : en mer, la liaison satellite est lente, chère ou coupée (voir l'[ADR-0009](../adr/0009-storage-and-offline-operation.fr.md)).

## 4. Une mission réussie

Une mission est réussie si elle **se termine avant le temps limite** (fin obligatoire : énergie des drones, météo) et si l'un des deux cas est vrai :

1. **L'épave est trouvée** : détectée puis confirmée.
2. **L'absence est démontrée** : la zone de recherche est couverte au-delà du **seuil de couverture** choisi par l'opérateur (95 % par défaut), et chaque **contact** est classifié par un drone spécialisé.

L'opérateur n'est jamais sûr que l'épave est dans la zone de recherche : démontrer son absence est donc aussi une réussite. Dépasser le **temps estimé** rend la mission en retard, mais pas en échec.

## 5. La métrique nord

**Probabilité de réussite de la mission.**

Avant la mission, le backend la calcule sur un **jeu de scénarios commun** : l'épave en position A, en position B, en position C… (réparties dans la zone de recherche), plus des scénarios sans épave. Chaque configuration de flotte est simulée sur exactement les mêmes scénarios, ce qui rend la comparaison juste et le résultat reproductible. La probabilité de réussite est la part des scénarios réussis : « la mission réussit dans 87 scénarios sur 100 ». C'est une prévision.

La **part de scénarios sans épave** est réglée par l'opérateur dans la mission (20 % par défaut) : elle dit à quel point il doute que l'épave soit dans la zone de recherche.

Avec des missions réelles, la métrique devient la **part des missions réelles réussies**.

## 6. Les garde-fous

| Garde-fou | Type | Seuil |
|---|---|---|
| Porte-drones perdu | strict | 0, jamais modifiable |
| Drones spécialisés perdus | strict | 0 par défaut, modifiable pour une mission avec l'accord du responsable |
| Drones de détection perdus | souple | le moins possible |

Une configuration qui viole un garde-fou **strict** est éliminée. Un garde-fou **souple** est surveillé, mais n'élimine rien.

## 7. Indicateurs secondaires

- **Temps estimé** : la durée prévue, c'est-à-dire le temps sous lequel 9 scénarios réussis sur 10 se terminent (la moyenne est donnée à côté). Plus il est court, plus il laisse de marge avant le temps limite.
- **Ressources engagées** : heures du porte-drones, drones engagés, heures de fonctionnement, énergie. Elles permettent d'estimer le coût d'une mission.
- **Usure de la flotte** : heures de fonctionnement, cycles de batterie, chocs contre le fond. Elle dit quand la flotte sera de nouveau disponible.

## 8. En cas d'égalité

Il n'y a pas de règle unique : selon la mission, l'opérateur préfère la sûreté, la rapidité ou l'économie de la flotte. Parmi les configurations qui respectent les garde-fous stricts, le backend en propose trois : **la plus sûre** (meilleure probabilité de réussite), **la plus rapide** (temps estimé le plus court) et **la plus économe** (moins de ressources engagées et d'usure). Si deux configurations sont à égalité sur leur critère, on garde celle qui perd le moins de drones de détection. L'opérateur choisit.
