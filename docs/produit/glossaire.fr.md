# Glossaire Gakkel

> English version: [`glossaire.md`](glossaire.md)

Cette page fixe les mots du projet. Tout le monde utilise le même mot pour la même
chose : dans la doc, sur les écrans, dans les issues et dans le code. Ces mots
deviendront les objets du code (le Domain-Driven Design appelle cela le *langage
omniprésent*).

Chaque définition est courte et lisible par des non-techniciens. La colonne **Dans le
code** indique si le mot existe déjà dans le code aujourd'hui (✅) ou s'il est prévu (🔜).

Plus d'infos sur l'utilisateur : [`persona-operateur.fr.md`](persona-operateur.fr.md).

---

## 1. La mission

| Mot | Définition | Dans le code |
|-----|------------|:------------:|
| **Mission** | Un travail confié à des flottes de drones, avec un objectif, une zone de recherche et une fenêtre de temps. Exemple : retrouver l'épave d'un avion dans une zone de recherche donnée. | 🔜 |
| **Campagne** | Une suite de missions préparées ensemble par l'opérateur. Une campagne contient plusieurs missions ; chaque mission garde son objectif, sa zone de recherche et sa fenêtre de temps. Exemple : fouiller une grande région en plusieurs missions, une zone de recherche après l'autre. Concept prévu pour plus tard. | 🔜 |
| **Mission de référence** | La mission qui sert à construire et tester Gakkel en premier : la **recherche civile d'une épave** (avion ou navire). | — |
| **Autres missions** | Les missions que Gakkel devra aussi gérer plus tard : **inspection**, **cartographie des fonds (bathymétrie)**, **guerre des mines**, **exploration scientifique** (prélever des échantillons, poser ou récupérer des instruments, mesurer l'eau). | — |
| **Objectif** | Ce que la mission doit atteindre. Exemple : « trouver l'épave », « cartographier cette zone ». | 🔜 |
| **Opérateur** | La personne qui prépare et suit la mission depuis le centre d'opérations. Gakkel aide l'opérateur ; c'est l'opérateur qui décide. | — |
| **Centre d'opérations** | La salle (souvent sur un navire de recherche) où travaille l'opérateur. Elle reçoit les informations envoyées par les porte-drones. | — |
| **État de la mission** | Où en est la mission dans sa vie. États proposés : *En préparation*, *Validée* (plan accepté), *En cours* (drones en mer), *Terminée*, *Annulée*. | 🔜 |
| **Moments de la mission** | Les moments clés pour l'opérateur : **Préparer**, **Lancer**, **Détecter**, **Confirmer**, **Intervenir**, **Clôturer**. | 🔜 |
| **GO / NO-GO** | La décision de lancer ou non, selon la météo et le trafic maritime. | 🔜 |
| **Fenêtre météo** | La période où la météo permet de mettre à l'eau et de récupérer les drones. | 🔜 |
| **AIS** | Le système qui donne la position des navires proches. L'opérateur le consulte avant le lancement. | 🔜 |
| **Enregistrer / ouvrir une mission** | L'opérateur peut enregistrer une mission et l'ouvrir plus tard, pour la réutiliser ou la modifier. | 🔜 |
| **Préparation en amont** | Préparer une mission à la base, avant de partir : l'opérateur planifie la mission à l'avance et décide quels drones et quel matériel embarquer. | 🔜 |
| **Préparation sur place** | Préparer une mission sur place, en mer : l'opérateur rouvre une mission préparée en amont et l'adapte à la situation réelle (météo, drones vraiment à bord, une panne). | 🔜 |
| **Rapport de mission** | Le bilan final : zones couvertes, contacts et leur statut, décisions prises. | 🔜 |

## 2. La flotte et les drones

| Mot | Définition | Dans le code |
|-----|------------|:------------:|
| **Drone** | Une machine sans équipage. Dans Gakkel, ce sont des drones sous-marins. Une flotte utilise des drones de détection et des drones spécialisés. | ✅ (*agent*) |
| **Flotte** | Une équipe qui travaille ensemble : **un porte-drones + un essaim + un ou plusieurs drones spécialisés**. L'opérateur coordonne plusieurs flottes. | 🔜 |
| **Porte-drones** | Il transporte l'essaim, le **dépose** dans la zone de recherche, parle avec l'essaim et relaie les informations au centre d'opérations. Il déploie aussi les drones spécialisés. | 🔜 (type réservé) |
| **Essaim** | Un groupe de **drones de détection** qui se déplacent ensemble, sans chef. L'essaim fait la **première détection** sur la zone de recherche. | ✅ |
| **Drone de détection** | Un drone de l'essaim. Il fouille la zone de recherche avec ses capteurs et signale ce qu'il trouve. | ✅ (*agent* `EXPLORER`) |
| **Drone spécialisé** | Un drone envoyé par le porte-drones pour un travail précis sur place. Il a un rôle ou plusieurs rôles, par exemple **qualifier un contact** (vérifier ce que c'est) ou **intervenir** dessus (récupérer la boîte noire, neutraliser une mine). Plus tard, d'autres rôles : **prélever** (des échantillons) ou **poser un instrument** (et le récupérer). | 🔜 (type réservé) |
| **Parc** | Tous les drones disponibles à la base, avec leur état : **prêt**, **en maintenance** ou **de rechange**. Un drone peut tomber en panne pendant une mission (ou pendant une simulation) ; le backend peut alors proposer des **drones de réserve** à emmener avec la flotte. | 🔜 |
| **Configuration de flotte** | Le choix des drones pour une mission : combien, quels types, quels réglages. Le backend peut tester beaucoup de configurations et proposer la meilleure. Mais c'est une aide, pas une obligation : l'opérateur peut aussi choisir la configuration lui-même. Aujourd'hui elle contient le nombre de drones de détection et le schéma de recherche. | ✅ (`FleetConfiguration`) |

## 3. Comment travaille une flotte : Déposer, Détecter, Confirmer, Intervenir

| Mot | Définition | Dans le code |
|-----|------------|:------------:|
| **Déposer** | Temps 1. Le porte-drones dépose l'essaim dans la zone de recherche. | 🔜 |
| **Détecter** | Temps 2. L'essaim, une fois à l'eau, fouille la zone de recherche avec ses capteurs. Quand un drone de détection voit un objet suspect, il signale un **contact** au porte-drones, qui le relaie à l'opérateur. À ce stade, un contact est seulement une cible *possible* : on ne sait pas encore si c'est l'épave ou un rocher. | ✅ (version simple) |
| **Confirmer** | Temps 3. Le porte-drones déploie un drone spécialisé pour aller voir un contact de près. Détecter dit « il y a quelque chose ici » ; Confirmer dit « oui, c'est l'épave » ou « non, ce n'en est pas une ». L'opérateur donne l'ordre, ou laisse Gakkel le faire automatiquement. | 🔜 |
| **Intervenir** | Temps 4. Un drone spécialisé traite le contact confirmé : par exemple il récupère la boîte noire ou neutralise une mine. Confirmer dit « c'est l'épave » ; Intervenir agit dessus. | 🔜 |
| **Envoi manuel / automatique** | Choisi pendant la préparation : l'opérateur envoie lui-même chaque drone spécialisé, ou Gakkel l'envoie automatiquement. L'opérateur peut reprendre la main à tout moment. | 🔜 |

## 4. La recherche

| Mot | Définition | Dans le code |
|-----|------------|:------------:|
| **Zone de recherche** | La partie de mer à fouiller, souvent 50 à 100 km². | ✅ (*world*) |
| **Schéma de recherche** | La façon dont les drones se déplacent pour couvrir la zone de recherche. C'est un réglage de la configuration de flotte. Schémas prévus pour l'étape 2 : **tondeuse** (lignes parallèles), **Boids** (déplacement libre en groupe), **tondeuse + Boids**, **colonies de fourmis** (les drones évitent les endroits déjà fouillés) et **colonies de fourmis + Boids**. Aujourd'hui seul Boids existe. | ✅ (`SearchPattern`, `BoidsSearchPattern`) |
| **Couverture** | La partie de la zone de recherche déjà fouillée. Le but : aucun trou et aucun recouvrement. | 🔜 |
| **Cible** | Dans la simulation, l'objet à trouver (l'« épave »). Aujourd'hui l'opérateur la place d'un clic dans la vue 3D. | ✅ |
| **Contact** | Quelque chose qu'un drone a détecté et qui pourrait être la cible. Un contact doit être vérifié. Statut : *à revoir*, *confirmé*, *écarté*. | 🔜 |
| **Détection** | Le moment où un drone passe assez près de la cible pour la « voir ». | ✅ |
| **Rayon de détection** | La distance à laquelle un drone peut détecter la cible (10 m aujourd'hui). Remplacé par la bande du sonar latéral à l'échelle réaliste ([ADR-0011](../adr/0011-model-assumptions-and-scale.fr.md)). | ✅ |
| **Sonar** | Le capteur sous-marin qui « voit » avec le son. Simulé plus tard. | 🔜 |
| **Sonar latéral** | Le capteur de recherche d'un drone de détection. Il voit une bande de fond de chaque côté (50 m), pas juste sous le drone (trou), et sa chance de détection baisse avec la distance. | 🔜 |
| **Fauchée** | La bande de fond qu'un capteur de recherche voit pendant que le drone avance. La tondeuse espace ses lignes à partir d'elle. | 🔜 |
| **Altitude de travail** | La hauteur au-dessus du fond où un capteur de recherche marche bien (15 m pour le sonar latéral). | 🔜 |
| **Suivi du fond** | Le drone garde son altitude de travail : si le fond monte, le drone monte. Le schéma de recherche ne décide que les déplacements horizontaux. | 🔜 |
| **Sonar frontal d'évitement** | Un capteur de navigation qui voit un cône devant le drone (environ 100 m). Un drone ne voit un obstacle que là. | 🔜 |
| **Fiche technique** | Les valeurs techniques d'un type de drone : vitesses, virage, altitude de travail, autonomie, capteurs, modem acoustique. Un objet-valeur : une fiche par type de drone ([ADR-0011](../adr/0011-model-assumptions-and-scale.fr.md)). | 🔜 |
| **Liaison acoustique** | La liaison sous l'eau (par le son) entre l'essaim et le porte-drones. Elle est lente et peut se couper. | 🔜 |
| **Modem acoustique** | L'appareil avec lequel chaque drone envoie sa position par le son (toutes les 10 s, portée d'environ 1 km, avec des messages perdus). Boids utilise ces positions annoncées. | 🔜 |
| **Score** | La **probabilité de réussite** d'une configuration de flotte : la part des scénarios réussis dans le jeu de scénarios commun. Exemple : « réussit dans 460 scénarios sur 500 » = 92 %. Le backend s'en sert pour comparer les configurations ([fiche problème](problem-find-a-wreck.fr.md)). | 🔜 |
| **Jeu de scénarios commun** | La liste des scénarios sur lesquels chaque configuration de flotte est simulée : l'épave en position A, B, C… (réparties dans la zone de recherche), plus des scénarios sans épave. Chaque scénario a sa seed. Toutes les configurations passent sur les mêmes scénarios, pour une comparaison juste. | 🔜 |
| **Part de scénarios sans épave** | La part du jeu de scénarios où l'épave n'est pas dans la zone de recherche. L'opérateur la règle dans la mission selon son doute (20 % par défaut). | 🔜 |
| **Temps limite** | L'heure à laquelle la mission doit être finie (énergie des drones, météo). Le dépasser, c'est un échec. | 🔜 |
| **Temps estimé** | La durée prévue d'une mission : le temps sous lequel 9 scénarios réussis sur 10 se terminent. Le dépasser rend la mission en retard, pas en échec. | 🔜 |
| **Seuil de couverture** | La couverture à atteindre pour démontrer que l'épave n'est pas dans la zone de recherche. Choisi par l'opérateur (95 % par défaut). | 🔜 |

## 5. Le comportement de l'essaim (Boids)

| Mot | Définition | Dans le code |
|-----|------------|:------------:|
| **Boids** | Un algorithme de Craig Reynolds (1986) : chaque drone suit des règles simples avec ses proches voisins, et un comportement de groupe apparaît. Pas de chef. | ✅ |
| **Séparation** | Règle : ne pas trop s'approcher de ses voisins. | ✅ |
| **Alignement** | Règle : aller dans la même direction que ses voisins. | ✅ |
| **Cohésion** | Règle : rester près du groupe. | ✅ |
| **Obstacle** | Quelque chose dans la zone de recherche que les drones doivent éviter (une sphère dans la simulation). | ✅ |
| **Limites** | Les bords de la zone de recherche (dans la simulation, les bords du monde). Les drones sont repoussés quand ils s'en approchent trop. | ✅ |
| **Danger** | Quelque chose que les drones doivent fuir ou éviter, parce qu'il peut les abîmer ou les bloquer : un navire en surface, un filet de pêche dérivant, un gros animal, une zone interdite. Sert à tester la réaction de l'essaim. On peut l'activer ou le désactiver dans la simulation. | ✅ (*predator*, bientôt renommé) |
| **Errance** | Un petit mouvement aléatoire pour que l'essaim n'ait pas l'air mécanique. | ✅ |

Détails et formules : [`../01-boids-rules.fr.md`](../01-boids-rules.fr.md).

## 6. Mots techniques

| Mot | Définition | Dans le code |
|-----|------------|:------------:|
| **Backend** | Le programme Java qui fait tourner la simulation. C'est le seul qui décide de ce qui se passe. | ✅ |
| **Vue 3D** (Unity) | L'affichage en 3D. Elle permettra de suivre une mission en temps réel et de la rejouer ensuite, en voyant les déplacements des drones. Elle montre seulement ce que le backend envoie ; elle ne décide rien. | ✅ |
| **Poste de contrôle** | L'application web de l'opérateur, où la mission est définie (dépôt `mission-console`). L'opérateur peut y créer une mission seul, de bout en bout, sans passer par la simulation. | 🔜 |
| **Vue détaillée / vue simple** | Les deux aspects de la vue 3D. La vue détaillée montre des modèles 3D et un vrai fond marin, pour présenter le projet ; la vue simple montre des formes simples, pour lire le comportement de l'essaim. La touche V bascule de l'une à l'autre ([ADR-0006](../adr/0006-detailed-and-simple-view.fr.md)). | ✅ |
| **Rejouer** (replay) | Revoir une mission terminée dans la vue 3D, pour voir comment les drones se sont déplacés et ce qui s'est passé. | 🔜 |
| **Simulation** | Un calcul sur ordinateur qui imite la mission, pour la tester avant d'aller en mer. C'est une aide pour l'opérateur, pas une étape obligatoire. | ✅ |
| **Agent** | Le nom d'un drone dans le code de la simulation. Son type dit son rôle : `EXPLORER` (drone de détection), `SPECIALIST` (drone spécialisé, type seulement réservé), `CARRIER` (porte-drones). | ✅ |
| **Tick** | Un pas de la simulation. Le backend calcule 30 ticks par seconde. | ✅ |
| **WorldState** | Une « photo » de la simulation à un instant : drones, obstacles, danger, cible. Le backend en envoie 20 par seconde. | ✅ |
| **Flux gRPC** | La connexion qui transporte les WorldState du backend vers la vue 3D, en continu. | ✅ |
| **Seed** (graine) | Un nombre qui fixe les tirages aléatoires. La même seed donne les mêmes positions de départ. | ✅ |
| **Horloge de simulation** | Un temps propre à la simulation, pas celui de l'ordinateur. Elle rendra les essais répétables et plus rapides que le temps réel. | 🔜 |
| **Optimiseur** | La partie du backend qui lance beaucoup de simulations et choisit la meilleure configuration de flotte grâce au score. | 🔜 |
| **NED** | Le repère utilisé sur le réseau entre le backend et la vue 3D : Nord, Est, Bas (profondeur). À l'intérieur, le backend utilise Est, Haut, Nord ([ADR-0007](../adr/0007-coordinate-frames.fr.md)). | ✅ |
