# Seabed — real relief from IBCAO / GEBCO bathymetry

> How the Unity seabed is built from real bathymetry data of the Gakkel Ridge.
> Version française : [`04-seabed-bathymetry.fr.md`](04-seabed-bathymetry.fr.md).

---

## 1. The area

| | |
|---|---|
| Place | Gakkel Ridge, Arctic Ocean, around the **Aurora hydrothermal field** |
| Centre | Aurora: 82.897° N, 6.255° W |
| Size | 20 km × 20 km square, aligned with true north |
| Depth | 2,679 m to 4,875 m (relief: about 2,200 m) |
| Depth at the centre (Aurora) | about 3,900 m |

The project is named after this ridge, so the demo scene uses its real seabed.

## 2. Which data, and why

| | GEBCO 2026 global grid | **GEBCO 2026 North Polar = IBCAO 5.2** (used) |
|---|---|---|
| Projection | latitude / longitude (EPSG:4326) | polar stereographic, metres (EPSG:3996) |
| Cell | 15 arc-seconds: **57 m × 463 m** at 83° N | **100 m × 100 m** |
| Steepest 1 % of slopes | about 26° | about 30° |

In the Arctic, the global GEBCO grid is made **from** IBCAO, then resampled to latitude /
longitude. Near the pole this loses detail north–south (one value every 463 m). The North Polar
download is the original 100 m IBCAO grid: small volcanic cones, scarps and ridges are visible.
Most of the area is covered by ship multibeam surveys; the smoother parts are interpolated.
Some survey artefacts (straight lines along the ship tracks, small steps) are part of the data.

Even at 100 m, cliffs smaller than a cell are smoothed: a grid value is an average over the cell.
This is why a vertical exaggeration is used (section 4).

Finer data exist for Aurora (ship multibeam gridded at 15–20 m in 2019, robot surveys at about
1 m), but we found no free direct download.

## 3. From GeoTIFF to Unity heightmap

The script [`tools/bathymetry_to_raw.py`](../tools/bathymetry_to_raw.py) works with any
projection the GeoTIFF declares (both files above):

1. reads the GeoTIFF (rasterio);
2. builds a 513 × 513 grid of points in a **local projection centred on Aurora**
   (azimuthal equidistant: metres east / north, true north up);
3. converts these points to the projection of the file (PROJ, through rasterio). In polar
   stereographic the grid is rotated compared with true north (about 6° here), so a simple crop
   would be turned;
4. reads the depth at each point with a **cubic spline** (a smooth curve through the data);
   a bilinear interpolation would leave "kinks" between cells, seen as regular stripes in Unity;
5. normalises the depths: deepest point = 0, shallowest point = 65535;
6. writes a 16-bit little-endian RAW file, south row first, to
   `unity-client/Assets/Resources/Seabed/aurora_heightmap_513.bytes`
   (`.bytes` so that Unity can load it as a `TextAsset`);
7. prints the depths and the size (the constants of `SeabedTerrain.cs`).

```
python -m venv tools/.venv
tools/.venv/Scripts/python -m pip install -r tools/requirements.txt
tools/.venv/Scripts/python tools/bathymetry_to_raw.py            # IBCAO file (default)
tools/.venv/Scripts/python tools/bathymetry_to_raw.py tools/data/gebco_2026_n83.0_s82.8_w-7.0_e-5.5_geotiff.tif
tools/.venv/Scripts/python -m pytest tools/tests
```

The heightmap is committed, so Unity does not need Python.

## 4. Scale in Unity

`SeabedTerrain.cs` builds the Terrain at runtime from the heightmap. `SwarmVisualizer` spawns it
in place of the old flat floor (and falls back to the flat floor if the file is missing).
In the `SwarmVisualizer` Inspector (object **GrpcManager**): **Use Bathymetry Seabed**,
**Seabed Vertical Exaggeration** (it can be changed in Play Mode), **Use Seabed Textures** and
**Seabed Focus Offset**. If you change a default value in the code, also change it in the
Inspector: the scene keeps the value it already has.

- **Horizontal: real size.** 1 Unity unit = 1 m, like the NED coordinates of the simulation.
  The Terrain is 20,000 m × 20,000 m, centred on Aurora.
- **Focus point.** The point of the Terrain placed under the swarm (metres from Aurora,
  X east / Y north). Default (700, −600): flat ground under the swarm, near a 47 m wall
  (140 m with ×3) within 300 m, 0.9 km from the Aurora vents. Set (0, 0) to be above the vents.
- **Vertical: adjustable exaggeration** (1 to 10). Terrain height = relief (2,196 m) × exaggeration.
  Default **3**: the grid smooths the slopes, and the seabed is mostly gentle at this scale.
  Bathymetric maps and profiles also use a vertical exaggeration, written in the legend.
  Far away the ridge walls rise above the simulated surface (Y = 0).
- **Vertical offset.** The simulation world is only 100 m deep, so the Terrain is moved up:
  the highest point of the seabed under the swarm footprint (100 × 50 m) is placed at
  Unity Y = −100 instead of about −4,000 m, so the relief never goes up into the swarm.
  Relief is real, absolute depth is not.

Today the swarm only covers about 100 m of this 20 km area. Making the simulation world match
the search area is planned in a separate issue.

## 5. Textures

With **Use Seabed Textures** on, `SeabedTerrain` paints three layers at startup, from the shape
of the seabed:

| Layer | Where | Tile size |
|---|---|---|
| Sediment (pale, fine) | flat ground and valleys | 12 m |
| Basalt (dark, cracked lava) | real slopes from 6° to 12°, and the higher ground | 15 m |
| Hydrothermal | within about 150 m of Aurora | 10 m |

The slopes are **real** slopes (the exaggeration is removed), so the painting does not change with
the exaggeration. Real slopes are gentle here (median 8°), hence the low thresholds: about 40 %
basalt overall. Large tiles avoid a visible repeating pattern.

Perlin noise breaks the straight borders between sediment and basalt.
Each layer has an albedo, a normal map and a mask map (1024 × 1024) in
`unity-client/Assets/Resources/Seabed/Textures/`. The Editor script `SeabedTextureImporter`
sets their import settings (normal map type, mask without sRGB).
The textures were made for this project.

With the option off, the seabed has one plain sand colour.

### Rocks

The bathymetry grid (about 40 m per sample) only shows hills. Seen from the drones, the seabed
looks smooth. With **Use Seabed Rocks** on, `SeabedRocks` places several hundred basalt boulders
(0.3 m to 4 m, mostly small) within 250 m of the swarm:

- many where the painting puts basalt (slopes, higher ground), few on sediment, none on the
  hydrothermal field;
- in clusters (Perlin noise), 30 % sunk into the sediment, tilted with the slope;
- only small rocks (1 m at most) under the swarm, so they stay out of its volume.

The shapes are made by code (a sphere of triangles, bumped, then cut by planes like broken lava),
so no 3D model is needed. The seed is fixed: the scene is the same at every start. The rocks follow
the seabed when the exaggeration changes in Play Mode. The **B** key shows or hides them in
Play Mode (hidden rocks cost nothing to draw).

## 6. Data sources and licence

Bathymetry (used): **IBCAO 5.2**, downloaded as "GEBCO 2026 – North Polar" from
[download.gebco.net](https://download.gebco.net/):
Jakobsson, M. et al. *The International Bathymetric Chart of the Arctic Ocean Version 5.0.*
Scientific Data 11, 1420 (2024), [doi:10.1038/s41597-024-04278-w](https://doi.org/10.1038/s41597-024-04278-w).
Included as part of the GEBCO_2026 Grid: **GEBCO Compilation Group (2026) GEBCO 2026 Grid**
(doi: [10.5285/4f68d5c7-45eb-f999e063-7086abc036fa](https://doi.org/10.5285/4f68d5c7-45eb-f999e063-7086abc036fa)),
Nippon Foundation-GEBCO Seabed 2030 Project. IBCAO is developed at Stockholm University.

The global GEBCO 2026 grid of the same area is kept in `tools/data/` for comparison.

[Terms of use](https://www.gebco.net/data-products/gridded-bathymetry-data) of the GEBCO Grid, in short:

- the GEBCO Grid is in the **public domain** and free to use, copy, adapt and publish;
- users must **credit the source** (this page and the README do it);
- no suggestion that GEBCO, the IHO or the IOC endorse this project;
- **not for navigation** or any purpose involving safety at sea.

The data and the derived heightmap stay under these terms; the project licence
(PolyForm Noncommercial) does not apply to them.
