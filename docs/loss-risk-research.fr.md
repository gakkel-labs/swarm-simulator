# Risque de perte des drones sous-marins : ce que disent les études

[English](loss-risk-research.md)

Ce document rassemble les études lues pour choisir les niveaux de **risque de perte**
(`LossRiskLevel`). La décision elle-même est dans
l'[ADR-0012](adr/0012-loss-risk-replaces-the-predator.fr.md). Le mot est défini dans le
[glossaire](produit/glossaire.fr.md).

## En bref

- Un drone sous-marin n'est presque jamais perdu parce que quelque chose le chasse. Il est perdu
  par des pannes ordinaires : fuite, batterie, navigation, implosion en profondeur.
- Une panne devient une perte surtout quand on ne peut pas aller chercher le drone : glace,
  grande profondeur, mauvais temps, navire de soutien loin ou bloqué.
- Le risque grandit avec la durée (ou la distance) de plongée. À panne égale, il est environ
  5 fois plus élevé sous la banquise et 9 fois sous une plateforme de glace qu'en eau libre.
- Presque toutes les études portent sur **un seul drone**. Le risque propre à un essaim reste un
  sujet de recherche ouvert.

## Comment les chercheurs mesurent le risque de perte

La référence est l'équipe de Southampton et du National Oceanography Centre (Mario Brito, Gwyn
Griffiths). Elle a commencé ce travail après la perte d'Autosub2 sous la glace en 2005. Leur
méthode tient en trois étapes ([Brito, Griffiths et Challenor, 2010](https://nora.nerc.ac.uk/id/eprint/169592/)) :

1. **Lister les pannes réelles.** Le journal d'Autosub3 compte 63 pannes ou incidents sur
   38 missions, soit 1 931 km parcourus.
2. **Demander à des experts.** Huit experts donnent, pour chaque panne, la probabilité qu'elle
   fasse perdre le drone dans quatre environnements : eau libre, eau côtière, sous la banquise,
   sous une plateforme de glace.
3. **Calculer une courbe de survie.** Un estimateur de Kaplan-Meier (une méthode classique de
   la médecine) donne la probabilité que le drone survive en fonction de la distance parcourue.

En 2016, les mêmes auteurs ajoutent le **navire de soutien** : plus il peut rejoindre vite un
drone en panne, plus le risque baisse. Un navire à 10 nœuds fait en général descendre le risque
d'une classe ; un navire bloqué dans la glace le fait monter d'une classe
([Brito et Griffiths, 2016](https://nora.nerc.ac.uk/id/eprint/512604/)). Pour Gakkel, c'est un
lien direct avec le **porte-drones**.

## Les chiffres trouvés

**Probabilité qu'une panne fasse perdre le drone** (avis médian des 8 experts, Autosub3,
[Brito et al. 2010, tableau I](https://nora.nerc.ac.uk/id/eprint/169592/))

| Environnement | Médiane | Quartile 25 % | Quartile 75 % | Par rapport à l'eau libre |
|---|---|---|---|---|
| Eau libre | 1,8 % | 0,83 % | 2,6 % | × 1 |
| Eau côtière | 2,1 % | 0,83 % | 3,7 % | × 1,2 |
| Sous la banquise | 8,8 % | 4,5 % | 17 % | × 4,9 |
| Sous une plateforme de glace | 17 % | 7,2 % | 40 % | × 9,4 |

**Probabilité de perte pour une mission polaire de 24 h**
([Journal of Marine Science and Engineering, 2023](https://www.mdpi.com/2077-1312/11/10/1976),
arbre de défaillance flou)

| Cause prise en compte | Probabilité de perte |
|---|---|
| Pannes internes seules | 0,18 % |
| Environnement polaire, bonnes conditions | 4,4 % |
| Environnement polaire, conditions moyennes | 13,2 % |
| Environnement polaire, mauvaises conditions | 35 % |

**Eaux côtières : deux REMUS-100 en Californie**
([Griffiths, Brito, Robbins et Moline, 2009](https://nora.nerc.ac.uk/id/eprint/169184/)).
C'est le meilleur jeu de données trouvé pour des drones commerciaux utilisés près des côtes.

| Mesure | Valeur |
|---|---|
| Missions | 189 (2003 à 2009), 4 031 km |
| Pannes ou incidents notés | 507, soit un tous les 8 km |
| Missions réussies | 91,5 % |
| Drones perdus | 0 |
| Risque de perte pour une mission de 14 km (avis d'experts) | 0,3 % (optimistes) à 1,9 % (pessimistes) |
| Distance moyenne avant une perte (avis d'experts) | 3 248 km (optimistes) à 504 km (pessimistes) |

Les auteurs jugent eux-mêmes les experts pessimistes trop pessimistes, puisque les deux drones
ont fait 189 missions sans perte.

**Une campagne entière.** Sous le glacier de Pine Island (Antarctique, 2009), l'équipe
estimait le risque de perdre Autosub3 entre 26 % et 96 % selon le scénario, avant mesures de
prudence. Le responsable acceptait 10 % à 23 %. Avec des essais en eau libre et une
surveillance des 48 premiers kilomètres, le scénario le plus court tombait à 3,5 %. Le drone
est revenu.

**Les planeurs sous-marins.** Pendant le développement du Seaglider, 8 des 10 premiers engins
ont été perdus, puis 2 des 12 suivants (chiffres de 2005, cités par Brito et al. 2010). Le
risque baisse beaucoup quand un modèle de drone mûrit.

## Hors zone polaire : côtes, structures, navires

Il y a moins de données hors des zones polaires. Aucune étude chiffrée n'a été trouvée sur le
travail près des plateformes de forage.

- **Près des structures.** Dans le journal des REMUS-100, les cas jugés les plus dangereux par
  les experts sont des collisions possibles : un drone arrêté sous un ponton au milieu des
  piliers, un drone parti vers les rochers après une erreur de balises. Une revue sur
  l'inspection des structures en mer note qu'un drone sans câble ne risque pas de s'emmêler,
  mais qu'on peut moins intervenir s'il a un problème
  ([JMSE, 2023](https://www.mdpi.com/2077-1312/11/11/2172)).
- **Navires en surface.** Pour les planeurs sous-marins en mer du Nord, Merckelbach transforme
  la densité de navires mesurée par l'**AIS** en carte de probabilité de perte par collision
  ([Merckelbach, 2013](https://link.springer.com/article/10.1007/s00773-012-0189-7), résumé
  seulement). Le poste de contrôle de Gakkel affiche déjà l'AIS.

## Ce qu'ajoutent la revue de 2021 et la thèse de 2023

La revue de 42 articles ([Chen, Bose, Brito et al., 2021](https://eprints.soton.ac.uk/451143))
et la thèse de Xi Chen (Memorial University of Newfoundland, 2023) confirment le modèle et
ajoutent quatre idées.

1. **Le risque dépend de la phase.** La mise à l'eau et la récupération sont les plus risquées ;
   la route en plongée est la plus sûre. Voir l'issue #178.
2. **Le milieu de la colonne d'eau est le plus sûr.** Dans un scénario de planeur, la perte
   monte près de la surface quand le trafic est dense, et près de la profondeur maximale du
   drone.
3. **Le risque change pendant la mission.** Une hausse des vagues ou un fort courant le fait
   monter en une heure. Un niveau fixe pour toute la mission est donc une simplification.
4. **Les missions à plusieurs drones sont peu étudiées.** Le risque propre à un essaim (deux
   drones qui se heurtent, communication entre drones) reste ouvert. C'est le terrain de
   Gakkel.

## Les causes réelles de perte

Le journal des 63 pannes d'Autosub3 montre ces familles :

- **Pannes techniques** : moteur, batterie, actionneur de gouvernail noyé, connecteurs qui
  prennent l'eau.
- **Navigation et communication** : GPS sans position, télémétrie acoustique muette, capteur
  de vitesse qui confond la surface et le fond.
- **Remontée et récupération** : ligne de récupération prise dans l'hélice, gouvernail ou
  antenne abîmés en remontant le drone à bord.
- **Collision avec le relief** : en 2009, Autosub3 a heurté la glace dans une fissure sous le
  glacier de Pine Island, puis il est revenu.
- **Erreurs humaines** : fabrication, assemblage, maintenance, réglage de mission.

Aucun chiffre n'a été trouvé sur les filets de pêche.

## Le cas des cheminées hydrothermales

Les drones ne traversent pas les gaz des cheminées : ils volent au-dessus et suivent le panache,
qui est justement ce qu'ils cherchent. En 2024, le drone Sentry a suivi un panache sur le mont
sous-marin Vailulu'u en volant plusieurs dizaines de mètres au-dessus du fond
([NOAA Ocean Exploration, 2024](https://oceanexplorer.noaa.gov/expedition/24autonomous-fluid-flow/)).
Le vrai danger est le relief : les cheminées sont hautes et irrégulières.

## Pertes célèbres

| Date | Drone | Où | Ce qui s'est passé |
|---|---|---|---|
| Mai 2014 | [Nereus](https://www.whoi.edu/press-room/news-release/nereus-lost/) (WHOI) | Fosse des Kermadec, 9 990 m | Implosion probable sous la pression. |
| Mars 2010 | [ABE](https://www.whoi.edu/press-room/news-release/pioneering-deep-sea-robot-lost-at-sea/) (WHOI) | Large du Chili, environ 3 000 m | Contact perdu en arrivant au fond ; implosion probable d'une sphère de flottaison. |
| 2005 | [Autosub2](https://nora.nerc.ac.uk/id/eprint/141098/) (NOC) | Sous la plateforme de glace Fimbulisen | Panne technique du drone. L'enquête conclut que les connecteurs et les fuites comptent plus que les menaces extérieures. |

La thèse de Xi Chen cite aussi des drones « perdus » puis retrouvés : Autosub Long Range près
de l'Irlande en 2014 (trois mois plus tard), un Hugin sous la banquise en 2019 (quatre jours
plus tard).

## Recoupement des niveaux de Gakkel

Les études parlent de distance ; Gakkel parle de **24 h de plongée**. Pour comparer, on
convertit avec la vitesse du drone (environ 6 km/h, soit 144 km en 24 h). Ces conversions sont
nos calculs, pas des chiffres publiés.

| Source | Chiffre publié | Converti en 24 h de plongée |
|---|---|---|
| JMSE 2023, pannes internes seules | 0,18 % par 24 h | 0,18 % |
| REMUS-100, observé | 0 perte en 4 031 km (environ 28 jours) | 0 % observé ; au plus environ 10 % (règle de 3) |
| REMUS-100, experts optimistes | une perte tous les 3 248 km | environ 4 % |
| REMUS-100, experts pessimistes | une perte tous les 504 km | environ 25 % |
| Autosub3, eau libre | 1 panne / 31 km, 1,8 % des pannes font perdre le drone | environ 7 % (avant mesures de prudence) |
| JMSE 2023, polaire, bonnes conditions | 4,4 % par 24 h | 4,4 % |
| JMSE 2023, polaire, conditions moyennes | 13,2 % par 24 h | 13,2 % |
| JMSE 2023, polaire, mauvaises conditions | 35 % par 24 h | 35 % |

La **règle de 3** est une règle statistique simple : après n essais sans aucun échec, le taux
d'échec est, avec 95 % de confiance, inférieur à environ 3/n. Ici, n est d'environ 28 jours de
plongée, d'où un plafond d'environ 10 % par jour.

| Niveau | Valeur | Sources qui l'appuient | Confiance |
|---|---|---|---|
| `NONE` | 0 % | Aucune : réglage pour la démo en direct | Sans objet |
| `LOW` | 0,2 % | JMSE 2023, pannes internes seules (0,18 %) | Bonne |
| `MEDIUM` | 2 % | Entre le 0 observé des REMUS-100 et les avis d'experts (4 % à 25 %) | Faible |
| `HIGH` | 5 % | JMSE 2023, polaire en bonnes conditions (4,4 %) ; experts optimistes REMUS (environ 4 %) | Moyenne |
| `EXTREME` | 15 % | JMSE 2023, polaire en conditions moyennes (13,2 %) | Moyenne |

Le facteur entre `MEDIUM` et `HIGH` ou `EXTREME` (2,5 à 7,5) est du même ordre que celui de
Brito 2010 entre l'eau libre et la glace (5 à 9).

`MEDIUM` est la valeur la moins sûre : aucune étude ne donne directement une perte par 24 h en
eaux côtières. Elle est plus basse que les avis d'experts, mais les drones réellement observés
n'ont rien perdu. Elle convient à un drone mûr ; pour un prototype, `HIGH` est plus prudent.

## Limites

- Les chiffres viennent surtout du **polaire** et d'**avis d'experts** sur un ou deux modèles
  de drones. Ce sont des ordres de grandeur.
- Le risque réel baisse beaucoup quand un modèle de drone mûrit (Seaglider : 8 pertes sur 10,
  puis 2 sur 12).
- Un seul niveau pour toute la mission ignore la phase (mise à l'eau, récupération), la
  profondeur et la météo.
- Rien de chiffré sur les filets de pêche, les plateformes de forage ou les chocs contre les
  cheminées.

## Sources

Lues pour ce document :

- Brito, Griffiths, Challenor, [Risk analysis for Autonomous Underwater Vehicle Operations in Extreme Environments](https://nora.nerc.ac.uk/id/eprint/169592/), *Risk Analysis* 30, 2010.
- Brito, Griffiths, [A Bayesian approach for predicting risk of AUV loss during their missions](https://nora.nerc.ac.uk/id/eprint/512604/), *Reliability Engineering & System Safety* 146, 2016.
- [Risk Analysis of Autonomous Underwater Vehicle Operation in a Polar Environment Based on Fuzzy Fault Tree Analysis](https://www.mdpi.com/2077-1312/11/10/1976), *Journal of Marine Science and Engineering* 11(10), 2023.
- Griffiths, Brito, Robbins, Moline, [Reliability of two REMUS-100 AUVs based on fault log analysis and elicited expert judgment](https://nora.nerc.ac.uk/id/eprint/169184/), UUST 2009.
- [Exploring Autonomous and Remotely Operated Vehicles in Offshore Structure Inspections](https://www.mdpi.com/2077-1312/11/11/2172), *Journal of Marine Science and Engineering* 11(11), 2023.
- Merckelbach, [On the probability of underwater glider loss due to collision with a ship](https://link.springer.com/article/10.1007/s00773-012-0189-7), *Journal of Marine Science and Technology*, 2013 (résumé seulement).
- Chen, Bose, Brito, Khan, Thanyamanta, Zou, [A Review of Risk Analysis Research for the Operations of AUVs](https://eprints.soton.ac.uk/451143), *Reliability Engineering & System Safety* 216, 2021.
- Xi Chen, [Risk analysis and decision making for autonomous underwater vehicles](https://memorial.scholaris.ca/items/b41d1287-e812-46d4-9c9e-2a056cef5d77), thèse, Memorial University of Newfoundland, 2023 (chapitres 2 et 3).
- Strutt, [Report of the inquiry into the loss of Autosub2 under the Fimbulisen](https://nora.nerc.ac.uk/id/eprint/141098/), NOC Southampton, 2006.
- WHOI, [Nereus](https://www.whoi.edu/press-room/news-release/nereus-lost/), 2014 ; [ABE](https://www.whoi.edu/press-room/news-release/pioneering-deep-sea-robot-lost-at-sea/), 2010.
- NOAA Ocean Exploration, [Autonomous Localization of Seafloor Fluid Flow Sources](https://oceanexplorer.noaa.gov/expedition/24autonomous-fluid-flow/), 2024.
