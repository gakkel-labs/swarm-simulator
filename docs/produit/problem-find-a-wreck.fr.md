# Fiche problème : trouver une épave

> English version: [`problem-find-a-wreck.md`](problem-find-a-wreck.md)

## 1. Le problème

L'opérateur prépare une mission de recherche d'une épave coulée. Il ne sait pas quelle configuration de flotte choisir (nombre de drones de détection, drones spécialisés, schéma de recherche), ni si l'épave se trouve vraiment dans la zone de recherche. Gakkel l'aide à choisir la configuration qui a le plus de chances de réussir, sans mettre en danger les drones les plus importants.

## 2. Une mission réussie

Une mission est réussie si elle **se termine avant le temps limite** (fin obligatoire : énergie des drones, météo) et si l'un des deux cas est vrai :

1. **L'épave est trouvée** : détectée puis confirmée.
2. **L'absence est démontrée** : la zone de recherche est couverte au-delà du **seuil de couverture** choisi par l'opérateur (95 % par défaut), et chaque **contact** est classifié par un drone spécialisé.

L'opérateur n'est jamais sûr que l'épave est dans la zone de recherche : démontrer son absence est donc aussi une réussite. Dépasser le **temps estimé** rend la mission en retard, mais pas en échec.

## 3. La métrique nord

**Probabilité de réussite de la mission.**

Avant la mission, le backend la calcule sur un **jeu de scénarios commun** : l'épave en position A, en position B, en position C… (réparties dans la zone de recherche), plus des scénarios sans épave. Chaque configuration de flotte est simulée sur exactement les mêmes scénarios, ce qui rend la comparaison juste et le résultat reproductible. La probabilité de réussite est la part des scénarios réussis : « la mission réussit dans 87 scénarios sur 100 ». C'est une prévision.

Avec des missions réelles, la métrique devient la **part des missions réelles réussies**.

## 4. Les garde-fous

| Garde-fou | Type | Seuil |
|---|---|---|
| Porte-drones perdu | strict | 0, jamais modifiable |
| Drones spécialisés perdus | strict | 0 par défaut, modifiable pour une mission avec l'accord du responsable |
| Drones de détection perdus | souple | le moins possible |

Une configuration qui viole un garde-fou **strict** est éliminée. Un garde-fou **souple** est surveillé, mais n'élimine rien.

## 5. Indicateurs secondaires

- **Temps estimé** : la durée prévue ; plus il est court, plus il laisse de marge avant le temps limite.
- **Ressources engagées** : heures du porte-drones, drones engagés, heures de fonctionnement, énergie. Elles permettent d'estimer le coût d'une mission.
- **Usure de la flotte** : heures de fonctionnement, cycles de batterie, chocs contre le fond. Elle dit quand la flotte sera de nouveau disponible.

## 6. En cas d'égalité

Il n'y a pas de règle unique : selon la mission, l'opérateur préfère la sûreté, la rapidité ou l'économie de la flotte. Parmi les configurations qui respectent les garde-fous stricts, le backend en propose trois : **la plus sûre** (meilleure probabilité de réussite), **la plus rapide** (temps estimé le plus court) et **la plus économe** (moins de ressources engagées et d'usure). Si deux configurations sont à égalité sur leur critère, on garde celle qui perd le moins de drones de détection. L'opérateur choisit.
