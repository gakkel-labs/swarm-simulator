# ADR-0006 : Proposer une vue détaillée et une vue simple de la scène Unity

[English](0006-detailed-and-simple-view.md)

## Statut
Proposé
Date : 2026-11-21

## Contexte
L'[ADR-0003](0003-minimal-3d-assets.fr.md) a choisi des formes simples (capsules, sphère,
cylindres) pour que le comportement de l'essaim reste facile à lire et que la scène reste légère.

L'étape 1 de la feuille de route demande une belle scène Unity pour présenter le projet. Depuis,
la scène a gagné des modèles 3D et du décor : le modèle de ROV pour les drones (#73), le vrai fond
marin avec textures et rochers (#75), une épave de navire comme cible de recherche, un
requin du Groenland comme prédateur, et des cheminées hydrothermales comme obstacles.

Ces deux besoins tirent dans des sens opposés :
- **Une démo** (une vidéo, une présentation) a besoin d'une scène qui ressemble à une vraie
  recherche sur le fond marin.
- **L'opérateur** suit une mission. Il veut savoir où sont les drones, ce qu'ils détectent et si
  l'essaim reste groupé. Le décor coûte du temps de rendu (plus de triangles, des textures) et peut cacher l'essentiel.

## Décision
Chaque élément décoratif de la scène a deux versions, et un seul interrupteur choisit entre elles :

| Élément | Vue détaillée | Vue simple |
|---|---|---|
| Drone | modèle de ROV | capsule |
| Prédateur | modèle de requin du Groenland | sphère rouge |
| Cible de recherche | modèle d'épave sur le fond : un ancien navire en bois (un modèle d'avion existe aussi) | forme de navire simple (boîtes, cylindre) |
| Obstacle | cheminée hydrothermale (cratère ouvert, fumée noire) | colonne sombre unie |
| Eau (#74) | brouillard bleu plus dense près du fond, lumière qui baisse avec la profondeur, caustiques, neige marine, bloom et vignette | brouillard léger seulement (la case **Underwater look** coupe toute l'ambiance) |

- La touche **V** bascule toute la scène entre vue détaillée et vue simple pendant le Play.
  La case **Show Rov Model** de `SwarmVisualizer` donne la vue de départ.
- Les rochers du fond ont leur propre interrupteur (touche **B**, **Use Seabed Rocks**), parce
  qu'ils coûtent le plus et ne sont liés à aucun objet de la simulation. La vue simple les cache toujours.
- Le code garde un seul état partagé, `DetailedView`. Chaque élément visuel l'écoute et montre
  l'une de ses deux versions. Ajouter un élément veut dire construire les deux versions.
- La vue simple reste la référence pour lire le comportement ; la vue détaillée sert à montrer
  le projet.

## Conséquences
+ La même scène sert pour une démo et pour suivre une mission.
+ Le coût du décor est optionnel : sur une petite machine, vue simple et pas de rochers.
+ Les modèles sont faits par des scripts (`tools/models/`, maillages générés par le code) : ils
  restent légers et on peut les refaire.
− Chaque nouvel élément visuel demande deux versions : un peu plus de travail.
− La vue détaillée montre des choses que la simulation ne connaît pas : l'épave est posée sur
  le fond sous le point cible. Cet écart disparaîtra quand le backend modélisera le fond et
  l'épave (le `Contact` à détecter).
− Pour l'instant, un obstacle est une sphère pour le serveur, mais il est dessiné comme une
  cheminée posée sur le fond : un drone qui passe sous la sphère traverse la cheminée à l'écran.
  La correction est côté serveur : des obstacles avec de vraies formes, comme une colonne
  verticale (#142).

## Alternatives envisagées
- **Vue détaillée seulement** : rejeté. Elle coûte du temps de rendu même quand personne ne
  regarde le décor, et elle rend l'essaim moins lisible.
- **Vue simple seulement (ADR-0003 telle quelle)** : rejeté pour l'étape 1. Le projet a besoin
  d'une scène qu'on comprend tout de suite comme une recherche sous-marine.
- **Un interrupteur par élément** : rejeté, sauf pour les rochers. Trop de touches pour la même idée.
- **Niveaux de qualité Unity** : rejeté pour l'instant. Ils changent les ombres et la résolution,
  pas les modèles affichés.
