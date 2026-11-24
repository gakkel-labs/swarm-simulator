# ADR-0011 : Utiliser une échelle réaliste, une fiche technique de drone et des capteurs réalistes pour le score

[English](0011-model-assumptions-and-scale.md)

## Status
Proposed
Date : 2026-11-24

## Contexte
À l'étape 2, les configurations de flotte sont notées par leur probabilité de réussite de la
mission (#82). Avant de noter quoi que ce soit, la simulation doit modéliser un problème qui
existe en mer. Aujourd'hui, ce n'est pas le cas :

| | Aujourd'hui | En mer (recherche civile d'une épave) |
|---|---|---|
| Zone de recherche | monde de 100 x 100 x 50 m (0,01 km²) | 50 à 100 km² (glossaire) |
| Capteur | une sphère de 10 m, en pleine eau, « vu ou pas vu » | un sonar latéral : une bande de fond de chaque côté, un trou sous le drone, une chance de détection qui baisse avec la distance, des fausses alarmes |
| Mouvement des drones de détection | Boids libre en 3D, peut glisser de côté | plonge, suit le fond à altitude fixe, tourne avec une vitesse de virage limitée, remonte |
| Vitesse | 5 m/s (environ 10 nœuds) | environ 1,5 m/s (3 nœuds) |
| Voisins (Boids) | chaque drone connaît la position et la vitesse exactes de chaque voisin | pas de GPS sous l'eau ; les drones entendent les positions envoyées par un modem acoustique, en retard, à courte portée, avec des messages perdus |
| Obstacles | connus partout | vus seulement par un sonar frontal, environ 100 m devant |
| Énergie | aucune | une autonomie fixe |
| Position sur la carte | aucune (mètres locaux seulement) | latitude / longitude, nécessaires à la carte marine de l'étape 3 |
| Pas de simulation | 1/30 s, temps réel | un batch doit simuler des heures de mission en quelques secondes |

La démo Unity en direct doit continuer de marcher : elle montre un petit monde, en temps réel,
et c'est elle qui sert à montrer le projet.

## Décision

### 1. Deux profils, un seul moteur
Le moteur et ses règles sont les mêmes. Seules les valeurs changent, lues dans la configuration.

| | `demo` (Unity, en direct) | `batch` (score #82, optimiseur #83) |
|---|---|---|
| Zone de recherche | 100 x 100 m, comme aujourd'hui | 7 x 7 km (environ 50 km²) |
| Pas de simulation | 1/30 s, temps réel | 1 s, aussi vite que possible |
| Fond marin | plat, comme aujourd'hui | plat à 1 000 m par défaut |
| Unity | oui | non |
| Valeurs des drones | celles d'aujourd'hui (elles rendent bien à l'écran) | la fiche technique ci-dessous |

La démo est un spectacle, pas une mesure : ses chiffres ne servent jamais à un score.

Pourquoi 1 s en batch : à 1,5 m/s, un drone avance de 1,5 m par pas, bien moins que la bande du
sonar, donc il ne peut pas « sauter » une épave. 16 heures de mission font 57 600 pas au lieu de
1,7 million. Les résultats dépendent du pas : il fait donc partie du profil batch et ne change
jamais entre deux configurations comparées.

### 2. Une fiche technique pour chaque type de drone
Une configuration de flotte dit « quel type de drone, combien ». Chaque type de drone a une
**fiche technique** : ses valeurs techniques. C'est un objet-valeur (un `record` Java, sans id,
qui ne change jamais). Chaque partie du moteur n'y lit que ce dont elle a besoin. Quand le
porte-drones et les drones spécialisés arriveront (#35), ils auront leur propre fiche, sans
changer le moteur.

Les fiches sont rangées dans un **catalogue de drones**, un fichier de configuration, pas dans le
code. Une configuration de flotte choisit des modèles dans le catalogue, avec un nombre pour
chacun : un essaim peut mélanger plusieurs modèles (par exemple des drones avec des capteurs
différents), selon ce qui est disponible (#176).

Drone de détection, valeurs `batch` (ordre de grandeur des petits drones de recherche prévus pour
les grands fonds) :

| Groupe | Valeur | Étape 2 |
|---|---|---|
| Mouvement | vitesse de croisière | 1,5 m/s (environ 3 nœuds) |
| | vitesse de virage max | 10°/s |
| | vitesse de plongée et de remontée | 0,5 m/s |
| | altitude de travail au-dessus du fond | 15 m |
| | profondeur max | 6 000 m (le fond de la démo, sur la dorsale de Gakkel, est à environ 3 900 m) |
| Énergie | autonomie | 16 h, soit une portée d'environ 86 km |
| Capteur de recherche | sonar latéral | 50 m de chaque côté, trou sous le drone ≈ altitude (15 m) |
| | chance de détection | 0,9 de 15 à 35 m, puis baisse en ligne droite jusqu'à 0 à 50 m |
| | faux contacts | environ 0,5 par km² de fond balayé |
| Capteurs de navigation | capteur de pression | profondeur du drone |
| | altimètre / loch Doppler | distance jusqu'au fond (jusqu'à 200 m) |
| | sonar frontal d'évitement | un cône de 100 m, large de 120°, devant le drone |
| Modem acoustique | portée | 1 km |
| | période | un message de position toutes les 10 s |
| | messages perdus | 15 % |

### 3. Les drones de détection bougent en 3D, avec deux décisions séparées
- Le **plan horizontal** (Nord, Est) est décidé par le schéma de recherche : lignes de la
  tondeuse, voisins Boids.
- La **verticale** est décidée par le **suivi du fond** : profondeur visée = profondeur du fond
  sous le drone − altitude de travail. Si le fond monte, le drone monte.

Pourquoi les séparer : le sonar latéral ne marche bien qu'à son altitude de travail. Si Boids
décidait aussi la verticale, deux drones pourraient s'empiler l'un au-dessus de l'autre et fouiller
la même bande de fond. Les vrais drones de recherche font pareil : un pilote de route dans le plan
horizontal, et une boucle d'altitude séparée.

Un drone de détection passe par trois phases :
1. **Plongée** depuis le point de dépose jusqu'à l'altitude de travail.
2. **Recherche** : il suit le fond. Le sonar ne compte que dans cette phase.
3. **Remontée** à la fin de son autonomie ou de la mission.

La plongée et la remontée prennent du temps et de l'autonomie : le score en tient compte.

Le fond est une fonction « profondeur du fond en (Nord, Est) ». Il est plat par défaut à
l'étape 2. Une pente ou un vrai relief pourront s'y brancher plus tard sans toucher au reste (voir
[bathymétrie du fond marin](../04-seabed-bathymetry.fr.md)).

### 4. Deux familles de capteurs
- Les **capteurs de recherche** voient une bande du fond et créent des **contacts**. Presque tous
  ont la même forme : une portée de chaque côté, un trou éventuel sous le drone, une courbe de
  détection, une altitude de travail. Seuls les chiffres changent. L'étape 2 ne code que le sonar
  latéral. Les missions suivantes ajouteront d'autres capteurs de recherche de la même forme :
  sonar à synthèse d'ouverture et magnétomètre (guerre des mines), sondeur multifaisceaux
  (cartographie), caméra (Confirmer).
- Les **capteurs de navigation** servent seulement à se déplacer sans rien heurter : profondeur,
  altitude, sonar frontal d'évitement. Un drone ne voit un obstacle que dans son cône frontal. Il
  passe par-dessus s'il peut, sinon il tourne.

Les mots de l'hydrographie, pour rappel : **fauchée** (la bande de fond vue), **portée** (un côté), **nadir** (le trou juste sous le drone), **espacement des lignes** et **recouvrement**.

```
          vue de face, le drone vient vers nous

                       ●  drone
                      /|\        altitude de travail : 15 m
                     / | \
                    /  |  \
  //////////////////   |   //////////////////   fond
  |<--- portée --->| nadir |<--- portée --->|
  |  de 15 à 50 m  | 2x15 m|  de 15 à 50 m  |
  |<---------------- fauchée --------------->|
```

La chance de détection est tirée avec la seed du scénario : le même scénario donne toujours le
même résultat (ADR-0010).

Les **faux contacts** (un rocher qui ressemble à une épave) sont comptés, mais ne changent pas le
score à l'étape 2 : Confirmer n'existe pas encore (#82 le laisse de côté).

La **couverture** compte le fond passé au moins une fois dans la bande utile du sonar (entre le
trou et la portée max). La chance de détection ne sert que pour l'épave.

### 5. Les voisins Boids viennent du modem acoustique
Les drones ne se voient pas avec leurs sonars : ils **s'écoutent**. Toutes les 10 s, chaque drone
envoie sa position et sa vitesse. Un drone ne connaît que les voisins à portée du modem, avec la
dernière position entendue (jusqu'à 10 s de retard, donc environ 15 m d'écart à 1,5 m/s). Quand
un message est perdu, il garde la dernière position connue. Boids (séparation, alignement,
cohésion) tourne sur ces positions annoncées. Le sonar frontal reste la sécurité si un voisin est
quand même trop près devant.

Boids reste ainsi au cœur du projet, et devient plus intéressant à montrer : un essaim qui reste
groupé avec des informations en retard et incomplètes, c'est exactement le problème réel.

### 6. L'écart des lignes de la tondeuse vient du capteur
Les lignes de la tondeuse (#156) sont espacées d'environ une portée d'un côté (50 m), pas de deux
fois la portée : le trou sous une ligne est alors vu par la ligne voisine. L'écart est un réglage
de la tondeuse : l'optimiseur peut essayer des lignes plus serrées (meilleure chance de
détection, plus de temps).

```
Coupe en travers de trois lignes voisines (1 caractère = 5 m, # = vu par le sonar)

              -50       0         50        100 m
              |         |         |         |
ligne C          #######                      drone à -50 m
ligne A       #######      #######            drone à 0 m
ligne B                 #######      #######  drone à 50 m
                     ^^^^^^  nadir de la ligne A : vu par C et B
```

Ordre de grandeur : un drone balaie 50 m x 1,5 m/s ≈ 0,27 km² par heure, donc 50 km² demandent
environ 185 heures-drone, soit environ 19 heures avec 10 drones. C'est plus que 16 h d'autonomie :
choisir le nombre de drones et l'écart des lignes, c'est justement le compromis que l'optimiseur
(#83) doit montrer.

### 7. La zone de recherche est liée à une vraie position
La zone de recherche a une **origine** : la latitude / longitude (WGS84) de son coin sud-ouest.
Les positions restent en mètres locaux, dans les repères de l'ADR-0007. La conversion utilise une
approximation « terre plate » :

```
nord_m = (latitude  − latitude_origine)  × 111 320
est_m  = (longitude − longitude_origine) × 111 320 × cos(latitude_origine)
```

Sur 10 km, l'erreur reste bien plus petite qu'une bande de sonar, même à la latitude de la
dorsale de Gakkel.

### 8. Ce qui est simplifié ou laissé de côté, et pourquoi

| Sujet | Étape 2 | Pourquoi |
|---|---|---|
| Position du drone lui-même (dérive sans GPS) | parfaite | l'erreur grandit lentement sur quelques heures ; elle comptera pour Confirmer, plus tard |
| Courant | aucun | pas utile pour comparer des configurations entre elles |
| Relief du fond | plat par défaut, fonction prête | le relief change les ombres du sonar, une étape plus tard |
| Énergie | autonomie fixe en heures | un modèle de batterie changerait peu le score |
| Type de fond, ombres du sonar, état de la mer | aucun | effets de second ordre |
| Porte-drones et drones spécialisés | aucun | pas de tels drones à l'étape 2 (#35) |
| Communication avec le porte-drones | aucune | l'essaim ne parle qu'à lui-même |

## Conséquences
+ Le score (#82) mesure un problème qui existe en mer : la bonne taille, la bonne vitesse, le bon
  capteur et le bon temps.
+ La démo Unity en direct marche sans changement : c'est le profil `demo`.
+ Les nouveaux types de drones et les nouveaux capteurs sont de nouvelles fiches, pas du nouveau
  code moteur.
+ Boids reste au cœur de l'essaim, avec des limites réalistes.
− Plus de valeurs à expliquer et à tenir à jour (la fiche technique). Ce sont des ordres de
  grandeur, pas les chiffres d'un vrai drone.
− Les batchs sont plus longs (des heures de mission par scénario). À mesurer dans #83.
− Plusieurs issues de suite avant que le score puisse tourner à cette échelle.

Issues de suite : fiche technique en code, profils demo et batch, mouvement 3D avec suivi du fond,
sonar latéral, voisins Boids par le modem acoustique, sonar frontal d'évitement, origine WGS84 de
la zone de recherche.

## Alternatives considérées
- **Garder le petit monde et multiplier les résultats** : rejetée. Le comportement d'un essaim ne
  grandit pas en ligne droite (voisins, bords, virages) : le score multiplié ne voudrait rien dire.
- **Un seul profil à l'échelle réelle pour tout, Unity compris** : rejetée. Un monde de 7 km avec
  des drones à 1,5 m/s ne montre rien à l'écran en temps réel, et la démo sert à montrer le projet.
- **Mouvement 3D libre décidé par Boids** : rejetée. Les drones s'empileraient et le sonar
  travaillerait hors de son altitude.
- **Garder la connaissance parfaite des voisins pour Boids** : rejetée. Elle n'existe pas sous
  l'eau, et elle ferait paraître Boids meilleur qu'il ne peut l'être en mer.
- **Un modèle acoustique détaillé (vitesse du son, bruit, échos multiples)** : rejetée pour
  l'instant. Une portée, une période et un taux de perte suffisent pour comparer des
  configurations.
