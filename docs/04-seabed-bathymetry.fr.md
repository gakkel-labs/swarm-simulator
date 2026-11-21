# Fond marin — vrai relief à partir de la bathymétrie IBCAO / GEBCO

> Comment le fond marin Unity est construit à partir de vraies données bathymétriques de la dorsale de Gakkel.
> English version: [`04-seabed-bathymetry.md`](04-seabed-bathymetry.md).

---

## 1. La zone

| | |
|---|---|
| Lieu | Dorsale de Gakkel, océan Arctique, autour du **champ hydrothermal Aurora** |
| Centre | Aurora : 82,897° N, 6,255° O |
| Taille | carré de 20 km × 20 km, orienté vers le nord géographique |
| Profondeur | de 2 679 m à 4 875 m (relief : environ 2 200 m) |
| Profondeur au centre (Aurora) | environ 3 900 m |

Le projet porte le nom de cette dorsale : la scène de démonstration montre son vrai fond.

## 2. Quelles données, et pourquoi

| | Grille mondiale GEBCO 2026 | **GEBCO 2026 North Polar = IBCAO 5.2** (utilisée) |
|---|---|---|
| Projection | latitude / longitude (EPSG:4326) | stéréographique polaire, en mètres (EPSG:3996) |
| Case | 15 secondes d'arc : **57 m × 463 m** à 83° N | **100 m × 100 m** |
| 1 % des pentes les plus raides | environ 26° | environ 30° |

En Arctique, la grille mondiale GEBCO est fabriquée **à partir** d'IBCAO, puis convertie en
latitude / longitude. Près du pôle, on perd du détail du nord au sud (une valeur tous les 463 m).
Le téléchargement North Polar est la grille IBCAO d'origine à 100 m : on y voit de petits cônes
volcaniques, des escarpements et des crêtes. La plus grande partie de la zone est couverte par des
relevés au sondeur multifaisceau de navires ; les parties plus lisses sont interpolées.
Quelques défauts de relevé (lignes droites le long des routes du navire, petites marches) font
partie des données.

Même à 100 m, les falaises plus petites qu'une case sont lissées : une valeur de la grille est une
moyenne sur la case. C'est pourquoi on utilise une exagération verticale (section 4).

Des données plus fines existent pour Aurora (sondeur de navire à 15–20 m en 2019, relevés de robots
à environ 1 m), mais nous n'avons pas trouvé de téléchargement direct et gratuit.

## 3. Du GeoTIFF à la heightmap Unity

Le script [`tools/bathymetry_to_raw.py`](../tools/bathymetry_to_raw.py) fonctionne avec toute
projection déclarée par le GeoTIFF (les deux fichiers ci-dessus) :

1. lit le GeoTIFF (rasterio) ;
2. construit une grille de 513 × 513 points dans une **projection locale centrée sur Aurora**
   (azimutale équidistante : des mètres vers l'est et le nord, le nord géographique en haut) ;
3. convertit ces points dans la projection du fichier (PROJ, via rasterio). En stéréographique
   polaire, la grille est tournée par rapport au nord géographique (environ 6° ici) : un simple
   découpage serait donc tourné ;
4. lit la profondeur en chaque point avec une **spline cubique** (une courbe lisse qui passe par
   les données) ; une interpolation bilinéaire laisserait des « plis » entre les cases, vus comme
   des rayures régulières dans Unity ;
5. normalise les profondeurs : point le plus profond = 0, point le moins profond = 65535 ;
6. écrit un fichier RAW 16 bits little-endian, en commençant par la ligne sud, dans
   `unity-client/Assets/Resources/Seabed/aurora_heightmap_513.bytes`
   (`.bytes` pour qu'Unity puisse le charger comme `TextAsset`) ;
7. affiche les profondeurs et la taille (les constantes de `SeabedTerrain.cs`).

```
python -m venv tools/.venv
tools/.venv/Scripts/python -m pip install -r tools/requirements.txt
tools/.venv/Scripts/python tools/bathymetry_to_raw.py            # fichier IBCAO (par défaut)
tools/.venv/Scripts/python tools/bathymetry_to_raw.py tools/data/gebco_2026_n83.0_s82.8_w-7.0_e-5.5_geotiff.tif
tools/.venv/Scripts/python -m pytest tools/tests
```

La heightmap est commitée : Unity n'a pas besoin de Python.

## 4. Échelle dans Unity

`SeabedTerrain.cs` construit le Terrain au lancement à partir de la heightmap. `SwarmVisualizer`
le crée à la place de l'ancien sol plat (et revient au sol plat si le fichier manque).
Dans l'Inspector de `SwarmVisualizer` (objet **GrpcManager**) : **Use Bathymetry Seabed**,
**Seabed Vertical Exaggeration** (modifiable en Play Mode), **Use Seabed Textures** et
**Seabed Focus Offset**. Si on change une valeur par défaut dans le code, il faut aussi la changer
dans l'Inspector : la scène garde la valeur qu'elle a déjà.

- **Horizontale : taille réelle.** 1 unité Unity = 1 m, comme les coordonnées NED de la simulation.
  Le Terrain fait 20 000 m × 20 000 m, centré sur Aurora.
- **Point focal.** Le point du Terrain placé sous l'essaim (en mètres depuis Aurora,
  X est / Y nord). Par défaut (700, −600) : un fond plat sous l'essaim, près d'un mur de 47 m
  (140 m avec ×3) à moins de 300 m, à 0,9 km des cheminées d'Aurora. Mettre (0, 0) pour être
  au-dessus des cheminées.
- **Verticale : exagération réglable** (de 1 à 10). Hauteur du Terrain = relief (2 196 m) × exagération.
  Par défaut **3** : la grille lisse les pentes, et le fond est surtout en pentes douces à cette échelle.
  Les cartes et coupes bathymétriques utilisent aussi une exagération verticale, notée en légende.
  Au loin, les murs de la dorsale dépassent la surface simulée (Y = 0).
- **Décalage vertical.** Le monde de la simulation ne fait que 100 m de profondeur : le Terrain est
  remonté : le point le plus haut du fond sous l'emprise de l'essaim (100 × 50 m) est placé à
  Y = −100 dans Unity, au lieu d'environ −4 000 m. Le relief ne monte donc jamais dans l'essaim.
  Le relief est réel, la profondeur absolue ne l'est pas.

Aujourd'hui, l'essaim ne couvre qu'environ 100 m de cette zone de 20 km. Faire correspondre le
monde de la simulation à la zone de recherche est prévu dans une issue séparée.

## 5. Textures

Avec **Use Seabed Textures** coché, `SeabedTerrain` peint trois couches au lancement, d'après la forme
du fond :

| Couche | Où | Taille de répétition |
|---|---|---|
| Sédiment (clair, fin) | fonds plats et vallées | 12 m |
| Basalte (lave sombre craquelée) | pentes réelles de 6° à 12°, et hauteurs | 15 m |
| Hydrothermal | à environ 150 m autour d'Aurora | 10 m |

Les pentes sont les pentes **réelles** (l'exagération est retirée) : la peinture ne change pas avec
l'exagération. Les vraies pentes sont douces ici (médiane 8°), d'où des seuils bas : environ 40 %
de basalte au total. De grandes tuiles évitent un motif répété visible.

Un bruit de Perlin casse les frontières droites entre sédiment et basalte.
Chaque couche a une albedo, une normal map et une mask map (1024 × 1024) dans
`unity-client/Assets/Resources/Seabed/Textures/`. Le script éditeur `SeabedTextureImporter`
règle leur import (type normal map, mask sans sRGB).
Les textures ont été faites pour ce projet.

Option décochée : le fond a une seule couleur sable unie.

### Rochers

La grille bathymétrique (environ 40 m par point) ne montre que des collines. Vu depuis les drones,
le fond paraît lisse. Avec **Use Seabed Rocks** coché, `SeabedRocks` pose plusieurs centaines de
blocs de basalte (de 0,3 m à 4 m, surtout des petits) dans un rayon de 250 m autour de l'essaim :

- beaucoup là où la peinture met du basalte (pentes, hauteurs), peu sur le sédiment, aucun sur
  le champ hydrothermal ;
- en groupes (bruit de Perlin), enfoncés à 30 % dans le sédiment, penchés selon la pente ;
- seulement des petits blocs (1 m au plus) sous l'essaim, pour ne pas entrer dans son volume.

Les formes sont faites par le code (sphère de triangles déformée, puis coupée par des plans, comme
de la lave cassée) : pas de modèle 3D à ajouter. La graine est fixe : la scène est la même à chaque
lancement. Les rochers suivent le fond quand on change l'exagération en Play Mode. La touche **B**
les affiche ou les cache en Play Mode (cachés, ils ne coûtent rien à dessiner).

## 6. Sources des données et licence

Bathymétrie (utilisée) : **IBCAO 5.2**, téléchargée comme « GEBCO 2026 – North Polar » sur
[download.gebco.net](https://download.gebco.net/) :
Jakobsson, M. et al. *The International Bathymetric Chart of the Arctic Ocean Version 5.0.*
Scientific Data 11, 1420 (2024), [doi:10.1038/s41597-024-04278-w](https://doi.org/10.1038/s41597-024-04278-w).
Incluse dans la grille GEBCO_2026 : **GEBCO Compilation Group (2026) GEBCO 2026 Grid**
(doi : [10.5285/4f68d5c7-45eb-f999e063-7086abc036fa](https://doi.org/10.5285/4f68d5c7-45eb-f999e063-7086abc036fa)),
projet Nippon Foundation-GEBCO Seabed 2030. IBCAO est développée à l'université de Stockholm.

La grille mondiale GEBCO 2026 de la même zone est gardée dans `tools/data/` pour comparaison.

[Conditions d'utilisation](https://www.gebco.net/data-products/gridded-bathymetry-data) de la grille GEBCO, en bref :

- la grille GEBCO est dans le **domaine public**, libre d'utilisation, de copie, d'adaptation et de publication ;
- il faut **citer la source** (cette page et le README le font) ;
- ne pas laisser entendre que GEBCO, l'OHI ou la COI approuvent ce projet ;
- **pas pour la navigation**, ni pour un usage lié à la sécurité en mer.

Les données et la heightmap qui en est tirée restent sous ces conditions ; la licence du
projet (PolyForm Noncommercial) ne s'y applique pas.
