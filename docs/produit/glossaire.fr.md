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
| **Rapport de mission** | Le bilan final : zones couvertes, contacts et leur statut, décisions prises. | 🔜 |

## 2. La flotte et les drones

| Mot | Définition | Dans le code |
|-----|------------|:------------:|
| **Drone** | Une machine sans équipage. Dans Gakkel, ce sont des drones sous-marins. Une flotte utilise des drones de détection et des drones spécialisés. | ✅ (*agent*) |
| **Flotte** | Une équipe qui travaille ensemble : **un porte-drones + un essaim + un ou plusieurs drones spécialisés**. L'opérateur coordonne plusieurs flottes. | 🔜 |
| **Porte-drones** | Il transporte l'essaim, le **dépose** dans la zone de recherche, parle avec l'essaim et relaie les informations au centre d'opérations. Il déploie aussi les drones spécialisés. | 🔜 (type réservé) |
| **Essaim** | Un groupe de **drones de détection** qui se déplacent ensemble, sans chef. L'essaim fait la **première détection** sur la zone de recherche. | ✅ |
| **Drone de détection** | Un drone de l'essaim. Il fouille la zone de recherche avec ses capteurs et signale ce qu'il trouve. | ✅ (*agent* `EXPLORER`) |
| **Drone spécialisé** | Un drone envoyé par le porte-drones pour un travail précis sur place. Il a un rôle ou plusieurs rôles, par exemple **qualifier un contact** (vérifier ce que c'est) ou **intervenir** dessus (récupérer la boîte noire, neutraliser une mine). Plus tard, d'autres rôles : **prélever** (des échantillons) ou **poser un instrument** (et le récupérer). | 🔜 |
| **Configuration de flotte** | Le choix des drones pour une mission : combien, quels types, quels réglages. Le backend peut tester beaucoup de configurations et proposer la meilleure. Mais c'est une aide, pas une obligation : l'opérateur peut aussi choisir la configuration lui-même. | 🔜 |

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
| **Schéma de recherche** | La façon dont les drones se déplacent pour couvrir la zone de recherche (par exemple lignes parallèles, spirale, ou déplacement libre en groupe). Aujourd'hui l'essaim se déplace seulement en groupe ; de vrais schémas sont prévus. | 🔜 |
| **Couverture** | La partie de la zone de recherche déjà fouillée. Le but : aucun trou et aucun recouvrement. | 🔜 |
| **Cible** | Dans la simulation, l'objet à trouver (l'« épave »). Aujourd'hui l'opérateur la place d'un clic dans la vue 3D. | ✅ |
| **Contact** | Quelque chose qu'un drone a détecté et qui pourrait être la cible. Un contact doit être vérifié. Statut : *à revoir*, *confirmé*, *écarté*. | 🔜 |
| **Détection** | Le moment où un drone passe assez près de la cible pour la « voir ». | ✅ |
| **Rayon de détection** | La distance à laquelle un drone peut détecter la cible (10 m aujourd'hui). | ✅ |
| **Sonar** | Le capteur sous-marin qui « voit » avec le son. Simulé plus tard. | 🔜 |
| **Liaison acoustique** | La liaison sous l'eau (par le son) entre l'essaim et le porte-drones. Elle est lente et peut se couper. | 🔜 |
| **Score** | Un nombre qui dit si une configuration de flotte est bonne pour une mission (par exemple : temps pour trouver la cible, couverture atteinte). Le backend s'en sert pour comparer les configurations. | 🔜 |

## 5. Le comportement de l'essaim (Boids)

| Mot | Définition | Dans le code |
|-----|------------|:------------:|
| **Boids** | Un algorithme de Craig Reynolds (1986) : chaque drone suit des règles simples avec ses proches voisins, et un comportement de groupe apparaît. Pas de chef. | ✅ |
| **Séparation** | Règle : ne pas trop s'approcher de ses voisins. | ✅ |
| **Alignement** | Règle : aller dans la même direction que ses voisins. | ✅ |
| **Cohésion** | Règle : rester près du groupe. | ✅ |
| **Obstacle** | Quelque chose dans la zone de recherche que les drones doivent éviter (une sphère dans la simulation). | ✅ |
| **Limites** | Les bords de la zone de recherche (dans la simulation, les bords du monde). Les drones sont repoussés quand ils s'en approchent trop. | ✅ |
| **Prédateur** | Un chasseur qui poursuit les drones ; les drones proches fuient. Sert à tester la réaction de l'essaim face à une menace. | ✅ |
| **Errance** | Un petit mouvement aléatoire pour que l'essaim n'ait pas l'air mécanique. | ✅ |

Détails et formules : [`../01-boids-rules.fr.md`](../01-boids-rules.fr.md).

## 6. Mots techniques

| Mot | Définition | Dans le code |
|-----|------------|:------------:|
| **Backend** | Le programme Java qui fait tourner la simulation. C'est le seul qui décide de ce qui se passe. | ✅ |
| **Vue 3D** (Unity) | L'affichage en 3D. Elle permettra de suivre une mission en temps réel et de la rejouer ensuite, en voyant les déplacements des drones. Elle montre seulement ce que le backend envoie ; elle ne décide rien. | ✅ |
| **Poste de contrôle** | L'application web de l'opérateur, où la mission est définie (dépôt `mission-console`). L'opérateur peut y créer une mission seul, de bout en bout, sans passer par la simulation. | 🔜 |
| **Rejouer** (replay) | Revoir une mission terminée dans la vue 3D, pour voir comment les drones se sont déplacés et ce qui s'est passé. | 🔜 |
| **Simulation** | Un calcul sur ordinateur qui imite la mission, pour la tester avant d'aller en mer. C'est une aide pour l'opérateur, pas une étape obligatoire. | ✅ |
| **Agent** | Le nom d'un drone dans le code de la simulation. Son type dit son rôle : `EXPLORER` (drone de détection), `CARRIER` (porte-drones). Attention : le type `OPERATOR` dans le code est seulement réservé et **n'est pas** l'opérateur humain. | ✅ |
| **Tick** | Un pas de la simulation. Le backend calcule 30 ticks par seconde. | ✅ |
| **WorldState** | Une « photo » de la simulation à un instant : drones, obstacles, prédateur, cible. Le backend en envoie 20 par seconde. | ✅ |
| **Flux gRPC** | La connexion qui transporte les WorldState du backend vers la vue 3D, en continu. | ✅ |
| **Seed** (graine) | Un nombre qui fixe les tirages aléatoires. La même seed donne les mêmes positions de départ. | ✅ |
| **Horloge de simulation** | Un temps propre à la simulation, pas celui de l'ordinateur. Elle rendra les essais répétables et plus rapides que le temps réel. | 🔜 |
| **Optimiseur** | La partie du backend qui lance beaucoup de simulations et choisit la meilleure configuration de flotte grâce au score. | 🔜 |
| **NED** | Le repère utilisé par le backend : Nord, Est, Bas (profondeur). | ✅ |
