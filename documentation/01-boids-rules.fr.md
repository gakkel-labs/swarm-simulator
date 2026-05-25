# Boids — Les 3 règles de Reynolds (+ boundary repulsion)

> Documentation du cœur algorithmique de la simulation.
> Référence : **Reynolds, C. W. (1986)** — *"Flocks, herds, and schools: A distributed behavioral model"*, ACM SIGGRAPH '87 Conference Proceedings, pp. 25–34.

---

## 1. Vue d'ensemble

Chaque agent (**boid**) applique à chaque tick les **3 règles de Reynolds** (séparation, alignement, cohésion) plus une **règle d'environnement** ajoutée hors modèle pur (`boundaryRepulsion`) pour gérer les frontières du monde. Chaque règle produit un **vecteur de direction unitaire**. Ces vecteurs sont pondérés et sommés, puis appliqués à la vitesse.

**Principe clé :** un agent ne perçoit que ses **voisins locaux** dans un rayon de perception unique (`perceptionRadius`). Pas de connaissance globale, pas de coordination centrale. Le flocking émergent vient de la composition de ces règles simples.

> **Note de cadrage :** les §3 à §5 décrivent les 3 règles de Reynolds. La §5.4 décrit `boundaryRepulsion`, qui n'est pas une règle Reynolds mais une nécessité pratique (cf. §11.5). La §6 montre comment les 4 sont combinées.

---

## 2. Schéma des règles

```mermaid
flowchart LR
    A[Agent i] --> P{Perception des voisins<br/>dans perceptionRadius}
    P --> S[Règle 1 : Séparation<br/>éviter les collisions]
    P --> AL[Règle 2 : Alignement<br/>aligner la vitesse]
    P --> C[Règle 3 : Cohésion<br/>rester groupé]
    P --> B[Règle 4 : BoundaryRepulsion<br/>éviter les bords du monde]
    P --> F[Règle 5 : PredatorFlee<br/>fuir le prédateur]
    S -->|× w_sep| SUM((Σ))
    AL -->|× w_align| SUM
    C -->|× w_coh| SUM
    B -->|× w_boundary| SUM
    F -->|× w_flee| SUM
    SUM --> V[Mise à jour vitesse<br/>v = clamp(v + steer·dt, MAX_SPEED)]
    V --> POS[Mise à jour position<br/>p += v · dt]
```

Version ASCII pour terminaux :

```
                    ┌─────────────────────┐
                    │   Voisins de i      │
                    │  (perceptionRadius) │
                    └──────────┬──────────┘
                               │
         ┌──────────────┬──────┴───────┬──────────────┬─────────────┐
         ▼              ▼              ▼              ▼             ▼
     SÉPARATION     ALIGNEMENT      COHÉSION       BOUNDARY     PRÉDATEUR
    (repousser)   (suivre cap)    (centroïde)   (éviter bords)    FUITE
         │              │              │              │             │
       × w_sep      × w_align       × w_coh      × w_boundary  × w_flee
         └──────────────┴──────┬───────┴──────────────┴─────────────┘
                               ▼
                          Σ steering
                               │
                               ▼
                 v = clamp(v + steer·dt, MAX_SPEED)
                          p += v · dt
```

---

## 3. Règle 1 — Séparation

**Objectif :** éviter de rentrer en collision avec les voisins proches.

**Principe :** pour chaque voisin perçu, générer un vecteur de répulsion pondéré par **1/d²** — plus le voisin est proche, plus la force est forte. Retourner la direction normalisée de la répulsion cumulée.

```text
fonction separation(agent, voisins):
    sum = (0, 0, 0)
    pour chaque v dans voisins:               # tous les voisins dans perceptionRadius
        away = agent.position - v.position
        d = away.magnitude()
        si d > ε:
            sum += away / (d * d)             # pondéré par 1/d² → plus proche = beaucoup plus fort
    si sum.magnitude() < ε:
        retourner (0, 0, 0)                   # aucun voisin, ou voisins exactement superposés
    retourner normalize(sum)                  # vecteur unitaire de direction
```

> **Note :** tous les voisins sont traités, pas seulement ceux dans un sous-rayon de séparation. La décroissance quadratique rend la répulsion **négligeable** aux bords du `perceptionRadius` (≈ 1/225 à d=15 vs ≈ 1 à d=1), ce qui rend un `separationRadius` dédié inutile en pratique. Le cutoff strict reste assuré par la couche de perception qui filtre les voisins en amont.

---

## 4. Règle 2 — Alignement

**Objectif :** aligner sa direction de déplacement sur celle des voisins perçus.

**Principe :** sommer les vecteurs vitesse des voisins, retourner la direction normalisée de cette somme.

```text
fonction alignment(voisins):
    si voisins est vide:
        retourner (0, 0, 0)
    sum = (0, 0, 0)
    pour chaque v dans voisins:
        sum += v.velocity
    si sum.magnitude() < ε:
        retourner (0, 0, 0)                   # vitesses qui s'annulent mutuellement
    retourner normalize(sum)                  # direction de la vitesse collective
```

---

## 5. Règle 3 — Cohésion

**Objectif :** rester groupé avec le flock.

**Principe :** calculer le **centroïde** (centre de masse) des voisins, retourner la direction normalisée vers ce point.

```text
fonction cohesion(agent, voisins):
    si voisins est vide:
        retourner (0, 0, 0)
    sum = (0, 0, 0)
    pour chaque v dans voisins:
        sum += v.position
    centroide = sum / count(voisins)
    diff = centroide - agent.position
    si diff.magnitude() < ε:
        retourner (0, 0, 0)                   # agent déjà au centroïde
    retourner normalize(diff)                 # direction vers le centre du groupe
```

---

## 5.4 Règle 4 — BoundaryRepulsion *(hors modèle Reynolds)*

**Objectif :** empêcher les agents de sortir du monde simulé. **Cette règle ne fait pas partie du modèle Reynolds 1987** — c'est un ajout pratique nécessaire (cf. §8 « Pas de frontière »).

**Principe :** pour chaque axe du monde, si l'agent est à moins de `boundaryRepulsionRadius` d'un mur, générer une force répulsive proportionnelle à la proximité (1 au contact, 0 à la frontière du rayon). Retourner la direction normalisée.

```text
fonction boundaryRepulsion(agent, world):
    push = (0, 0, 0)
    pour chaque axe dans (x, y, z):
        dist_min = agent.position[axe] - world.min[axe]
        dist_max = world.max[axe] - agent.position[axe]
        si dist_min < boundaryRepulsionRadius:
            push[axe] += 1 - dist_min / boundaryRepulsionRadius
        si dist_max < boundaryRepulsionRadius:
            push[axe] -= 1 - dist_max / boundaryRepulsionRadius
    si push.magnitude() < ε:
        retourner (0, 0, 0)                   # agent loin de toutes les frontières
    retourner normalize(push)
```

---

## 5.5 Règle 5 — PredatorFlee *(réponse à la menace, hors modèle Reynolds)*

**Objectif :** repousser chaque agent loin du prédateur autonome dès qu'il entre dans `threatFleeRadius`. Le prédateur est une **menace unique en mouvement**, pas un voisin du flock — il est traité séparément de la liste de voisins filtrée par `perceptionRadius`.

**Principe :** répulsion inverse-carré (même noyau que la Séparation). La règle n'est **pas normalisée** — la magnitude brute croît à mesure que le prédateur se rapproche, déclenchant une fuite d'urgence réactive plutôt qu'une dérive constante.

```text
fonction predatorFlee(agent, prédateur):
    away = agent.position - prédateur.position
    d = away.magnitude()
    si d > threatFleeRadius ou d < ε :
        retourner (0, 0, 0)           # prédateur hors portée, ou co-localisé (dégénéré)
    retourner away / (d * d)          # NON normalisé — la magnitude encode l'urgence
```

> **Différence avec la Séparation :** `separation` est normalisé (magnitude toujours = 1) ; `predatorFlee` ne l'est pas. Combiné à `threatFleeWeight = 200.0`, la force de fuite écrase toutes les forces de flocking à quelques mètres — la panique l'emporte sur la formation. Au-delà de `threatFleeRadius` la force est exactement nulle.

---

## 6. Composition

Le vecteur de steering total appliqué à l'agent à chaque tick :

```text
steer = w_sep      * separation(agent, voisins)
      + w_align    * alignment(voisins)
      + w_coh      * cohesion(agent, voisins)
      + w_boundary * boundaryRepulsion(agent, world)
      + w_flee     * predatorFlee(agent, prédateur)   # seulement si prédateur != null et en portée

agent.velocity += steer * dt
agent.velocity  = clamp(agent.velocity, MAX_SPEED)   # seul plafond appliqué
agent.position += agent.velocity * dt
```

> **Note :** les règles 1 à 4 retournent des vecteurs unitaires (magnitude = 1). `predatorFlee` (règle 5) retourne un vecteur non normalisé — sa magnitude croît en 1/d² dans `threatFleeRadius`. Il n'y a pas de `clamp(steer, MAX_FORCE)` — la magnitude totale de `steer` est pratiquement bornée par la somme des poids, le terme de fuite décroissant à zéro hors du rayon.

---

## 7. Coefficients et leur effet

Valeurs par défaut (classe `BoidsConfig`) :

| Paramètre                  | Valeur par défaut | Rôle                                                            | Effet si augmenté                          |
| -------------------------- | ----------------- | --------------------------------------------------------------- | ------------------------------------------ |
| `perceptionRadius`         | `15.0`            | Rayon unique de perception pour toutes les règles               | Voisinage plus grand, comportement global  |
| `separationWeight`         | `1.5`             | Poids de la séparation dans la somme finale                     | Flock plus lâche, espacement plus grand    |
| `alignmentWeight`          | `1.0`             | Poids de l'alignement                                           | Mouvements plus parallèles, effet "banc"   |
| `cohesionWeight`           | `1.0`             | Poids de la cohésion                                            | Flock plus serré, plus compact             |
| `maxSpeed`                 | `5.0`             | Vitesse maximale de l'agent                                     | Flock plus rapide, plus réactif            |
| `boundaryRepulsionRadius`  | `15.0`            | Distance aux murs en-dessous de laquelle la répulsion s'active  | Marge de sécurité plus grande aux bords    |
| `boundaryRepulsionWeight`  | `2.0`             | Poids de la répulsion aux bords                                 | Agents s'éloignent des murs plus tôt/fort  |
| `threatFleeRadius`         | `15.0`            | Rayon de détection du prédateur — la fuite s'active en dessous  | Zone de panique plus large                 |
| `threatFleeWeight`         | `200.0`           | Poids de predatorFlee dans la somme finale                      | Réponse de panique plus/moins forte        |
| `dt` *(simulation loop)*   | `1/30 s ≈ 33 ms`  | Pas d'intégration temporelle (30 Hz)                            | Pas plus grands → instabilité numérique    |
| `ε` *(garde numérique)*    | `1e-6`            | Seuil sous lequel un vecteur est considéré nul                  | Trop grand → règles inopérantes près de 0  |

**Règles empiriques :**
- `w_sep` légèrement supérieur à `w_align` et `w_coh` → évite les collisions tout en préservant le flocking.
- `boundaryRepulsionWeight` > `cohesionWeight` → les bords sont plus prioritaires que rester groupé.
- Augmenter `perceptionRadius` sans ajuster les poids rend le comportement plus "global" — le flock se comporte comme un seul bloc.

---

## 8. Limitations connues du modèle pur

| Limitation                | Description                                                                                                | État dans ce projet / mitigation                              |
| ------------------------- | ---------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------- |
| **Overshooting**          | Un agent dépasse sa cible (centroïde) et oscille autour. Le terme `desired − velocity` du modèle Reynolds pur amortirait naturellement, mais il est absent ici (cf. §11.2). | Non mitigé. Solutions futures : ajouter le steering Reynolds (cf. §12) ou un terme de damping explicite. |
| **Oscillation**           | À l'équilibre, des forces opposées font vibrer l'agent autour d'un point fixe.                              | Non mitigé. Zone morte sur la vitesse possible.               |
| **Optima locaux**         | Un flock peut se scinder en sous-groupes stables, sans mécanisme pour les réunir.                          | Non mitigé. Règle "wandering" ou attracteur global possibles. |
| **Pas de frontière**      | Le modèle pur Reynolds ne définit pas ce qui se passe au bord du monde.                                     | ✅ **Résolu hors modèle** via `boundaryRepulsion` (cf. §5.4 / §11.5). |
| **Complexité O(N²)**      | À chaque tick, chaque agent parcourt tous les autres pour trouver ses voisins.                              | Non mitigé en v0.1. Spatial hashing / k-d tree / octree post-MVP. |
| **Pas d'évitement d'obstacles** | Le modèle ne tient compte que des autres agents, pas de l'environnement statique.                     | Non mitigé. Règle séparée avec raycast / SDF possible.        |
| **Pas de but**            | Un flock n'a pas d'objectif. Il bouge mais ne va nulle part en particulier.                                 | Non mitigé. Leader, waypoint, force d'attraction externe possibles. |
| **Sensibilité au dt**     | L'intégration numérique (`p += v·dt`) devient instable à grand `dt`.                                        | Mitigé : `dt` fixé à 33 ms par la boucle 30 Hz. Sous-pas si nécessaire. |
| **2D vs 3D**              | Les formules sont identiques, mais le tuning diffère : la 3D demande des poids plus faibles.                | À tuner si bascule en 3D.                                     |
| **Absence de prédateur**  | Reynolds 1987 mentionne une règle "avoid predator" comme extension naturelle. | ✅ **Résolu en v0.1** via `predatorFlee` (§5.5). Un prédateur autonome se dirige vers l'agent le plus proche à 3 m/s ; les boids dans un rayon de 15 m fuient avec une répulsion inverse-carré (`w_flee = 200`). Les agents mangés réapparaissent 5 s plus tard loin du prédateur. |

---

## 9. Références

- **Reynolds, C. W.** (1987). *Flocks, herds and schools: A distributed behavioral model.* Dans *Proceedings of the 14th annual conference on Computer graphics and interactive techniques* (SIGGRAPH '87), pp. 25–34. ACM. [DOI : 10.1145/37401.37406](https://doi.org/10.1145/37401.37406)
  > *Note : le papier est souvent daté 1986 (année de soumission) ou 1987 (année de publication). La référence "Reynolds 1986" est la convention courante.*
- Reynolds, C. W. — [Boids — Background and Update](https://www.red3d.com/cwr/boids/) (page personnelle de l'auteur, contient pseudocode et démos).

---

## 10. Notes d'implémentation (gakkel-swarm-simulator)

- Les règles sont implémentées dans `BoidsRules.java` (module `swarm-server`).
- Les coefficients sont dans `BoidsConfig.java` (record Java 21) et seront hot-reloadables post-MVP.
- Les tests unitaires dans `BoidsRulesTest.java` (cf. issue #7) couvrent les 3 règles Reynolds isolément + `boundaryRepulsion`.
- La boucle de simulation applique les 4 règles à 30 Hz (cf. issue #6).

---

## 11. Choix d'implémentation et écarts au modèle pur

L'implémentation diffère du modèle théorique de Reynolds sur 5 points. Cette section est destinée au lecteur qui comparerait le code avec le papier original.

### 11.1 Séparation : 1/d² au lieu de 1/d

| | Reynolds pur | Implémentation |
|---|---|---|
| Pondération | `normalize(diff) / d` — décroissance linéaire | `away / d²` — décroissance quadratique |
| Rayon dédié | `SEPARATION_RADIUS` < `NEIGHBOR_RADIUS` | aucun — tous les voisins perçus sont traités |

**Pourquoi :** La décroissance quadratique crée une "bulle personnelle" plus tranchée. Un voisin à moitié de la distance exerce 4× plus de force, pas 2×. Avantage direct : la répulsion devient négligeable aux bords du `perceptionRadius` (cf. §3), ce qui évite d'avoir un second paramètre `SEPARATION_RADIUS`.

### 11.2 Règles en direction pure — sans formule de steering Reynolds

Reynolds construit un vecteur de steering complet dans chaque règle :
```
desired = normalize(desired) * MAX_SPEED
steer   = desired − agent.velocity     ← correction relative à la vitesse courante
steer   = clamp(steer, MAX_FORCE)
```

L'implémentation retourne `normalize(sum)` — un vecteur unitaire de direction. La composition des poids et l'intégration se font en dehors, dans `steer()` et la boucle de simulation.

**Corollaire :** la division par `n` (moyenne) avant normalisation, présente dans Reynolds, est omise — `normalize(sum)` et `normalize(sum/n)` donnent le même résultat (la direction est invariante par scalaire positif).

**Pourquoi :** Séparation des responsabilités. Les règles expriment *où aller*, pas *comment accélérer*. Chaque règle est testable unitairement sans simuler de vitesse courante.

### 11.3 Rayon unique, voisins pré-filtrés

Reynolds distingue deux zones : `SEPARATION_RADIUS` (répulsion) < `NEIGHBOR_RADIUS` (alignement + cohésion).
L'implémentation n'a qu'un seul `perceptionRadius` — la liste de voisins est filtrée **en amont** par la couche de perception, avant d'arriver aux règles.

**Pourquoi :** La décroissance en 1/d² remplace le rayon de séparation dédié. Un seul paramètre à tuner. Les règles restent stateless sur la notion de distance.

### 11.4 Pas de MAX_FORCE — borne implicite par la normalisation

Reynolds clamp chaque vecteur de steering à `MAX_FORCE`.
L'implémentation ne dispose pas de ce paramètre.

**Pourquoi :** Chaque règle retourne un vecteur unitaire (magnitude = 1). La magnitude totale de `steer` est donc bornée **supérieurement** par la somme des poids : `1.5 + 1.0 + 1.0 + 2.0 = 5.5`. Cette borne n'est atteinte que si tous les vecteurs sont colinéaires ; en pratique la magnitude réelle est bien inférieure. Le clamping explicite n'apporte rien dans cette conception.

### 11.5 Quatrième règle : boundaryRepulsion (ajout)

Le modèle pur à 3 règles ne gère pas les frontières (cf. §8). L'implémentation ajoute `boundaryRepulsion()` (cf. §5.4), exposée via une surcharge `steer(agent, neighbors, world)`.

**Pourquoi :** Nécessité pratique. Sans répulsion aux bords, tous les agents quittent le monde simulé en quelques secondes. La surcharge sans `world` est conservée pour les tests unitaires qui n'ont pas besoin de monde.

### Résumé des écarts

| Écart | Reynolds pur | Implémentation | Raison |
|---|---|---|---|
| Séparation | 1/d, rayon dédié | 1/d², tous les voisins | Bulle marquée, un seul rayon |
| Steering | `desired − velocity` + clamp | direction pure normalisée | Séparation des responsabilités |
| Rayons | 2 (`SEPARATION_RADIUS` + `NEIGHBOR_RADIUS`) | 1 (`perceptionRadius`) | 1/d² compense |
| MAX_FORCE | paramètre explicite | borne implicite par les poids | Borné structurellement |
| Frontières | non géré | `boundaryRepulsion` (4ème règle) | Nécessité pratique |

---

## 12. Retour au modèle Reynolds pur — effort et gains

### 12.1 Effort estimé

Revenir au modèle pur est une refactorisation mécanique. Voici les changements fichier par fichier :

**`BoidsConfig.java`** — ajouter 2 champs :
- `separationRadius` (double, validation > 0 et < perceptionRadius)
- `maxForce` (double, validation > 0)

**`BoidsRules.java`** — 3 méthodes modifiées :

| Méthode | Changement |
|---|---|
| `separation(agent, neighbors)` | Filtrer par `separationRadius`. Remplacer `1/d²` par `1/d`. Ajouter la formule Reynolds : `normalize(sum) * maxSpeed − agent.velocity()`, puis `clamp(maxForce)`. |
| `alignment(neighbors)` | Ajouter le paramètre `agent`. Diviser par `n` avant de normaliser. Appliquer `normalize * maxSpeed − velocity`, puis `clamp`. |
| `cohesion(agent, neighbors)` | Diviser par `n` (déjà fait pour le centroïde). Appliquer `normalize * maxSpeed − velocity`, puis `clamp`. |
| `steer(agent, neighbors)` | Simplifier — les règles retournent déjà le steer complet, plus besoin de multiplier par les poids... ou conserver les poids à ce niveau (les deux sont valides). |

**`BoidsRulesTest.java`** — mettre à jour les signatures et les assertions (les tests deviennent plus riches : on peut vérifier que le steer est borné à `maxForce`, et que la vitesse courante influence le résultat).

**Estimation totale : 2 à 3 heures**, dont la majorité sur les tests. Le risque principal est le retuning des coefficients — le comportement visuel changera.

### 12.2 Gains attendus

| Gain | Description |
|---|---|
| **Décélération naturelle** | Le terme `desired − velocity` agit comme un amortisseur implicite. Si l'agent va déjà dans la bonne direction à bonne vitesse, la correction est quasi nulle. Réduit l'overshooting et les oscillations autour du centroïde. |
| **Manœuvrabilité explicitement contrôlable** | `MAX_FORCE / MAX_SPEED` devient le "rayon de virage" du boid. On peut tuner finement la fluidité sans toucher aux poids Boids. |
| **Séparation zone-propre franche** | Avec `SEPARATION_RADIUS` < `perceptionRadius`, les agents ne se repoussent que dans leur bulle immédiate. Hors de cette zone, la cohésion et l'alignement ne sont plus perturbés par une micro-répulsion résiduelle. |
| **Comportement scalable au nombre de voisins** | La division par `n` dans alignment/cohesion évite qu'un agent au centre d'un dense flock reçoive un steer "dilué" en direction. Un grand flock se comporte comme un petit. |
| **Conformité au papier original** | Le code est directement comparable au pseudocode Reynolds 1987 — plus facile à expliquer, à déboguer, et à faire évoluer (règle "avoid predator", arrival behavior, etc.). |
| **Tests plus expressifs** | Avec `MAX_FORCE` borné, on peut écrire `assertThat(steer.magnitude()).isLessThanOrEqualTo(maxForce)` — une invariante de sécurité absente aujourd'hui. |

### 12.3 Ce qui ne changerait pas

- La règle `boundaryRepulsion` reste une addition hors-modèle nécessaire.
- La complexité O(N²) de perception reste inchangée — c'est un problème séparé (spatial hashing, post-MVP).
- Le comportement émergent global (flocking) resterait similaire — les écarts actuels n'empêchent pas le flocking, ils le rendent juste moins précis théoriquement.

### 12.4 Recommandation

Implémenter la version pure **avant** d'ajouter des comportements applicatifs SAR — Search and Rescue (leader, waypoint, évitement d'obstacles, arrival behavior sur une cible). Le terme `desired − velocity` est particulièrement utile pour les règles d'arrivée — sans lui, un agent fonçant vers un waypoint n'a aucun mécanisme naturel de ralentissement.
