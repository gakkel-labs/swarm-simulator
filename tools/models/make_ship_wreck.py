"""Builds the ship wreck model used as the search target in the Unity scene.

The ship is a generic 19th-century wooden three-masted ship (about 36 m long), the kind
used for polar exploration. It is not a model of a real ship. An old wreck has no beacon:
drones find it with side-scan sonar (Detect), then look at it with a camera (Confirm).

The wreck sits upright but leans to one side, half buried in the sediment. Two masts are
broken, the third one lies on the seabed. A hole in the side shows the frames inside.
Mineral crust and sediment cover parts of the wood.

Conventions: metres, Y up, +Z towards the bow (glTF convention, which is also Unity's forward),
pivot on the seabed (y = 0) at the centre of the wreck. Parts below y = 0 are buried.

Usage:
    pip install -r tools/models/requirements.txt
    python tools/models/make_ship_wreck.py unity-client/Assets/Models
    python tools/models/make_ship_wreck.py unity-client/Assets/Models --preview preview.png
"""
import argparse
from pathlib import Path

import numpy as np
import trimesh
from trimesh import transformations

from glb_export import write_glb, write_preview

RANDOM = np.random.default_rng(1881)  # fixed seed: same wreck every time

HULL_LENGTH = 36.0
HULL_HALF_BEAM = 4.2
HULL_DEPTH = 5.5
STATIONS = 36
PROFILE_POINTS = 10

MATERIALS = {
    # name: (base colour RGBA, metallic, roughness)
    # Ships of that time: black painted hull, a coloured band along the side, and copper
    # sheathing below the waterline (against shipworms), now green with verdigris.
    "Wood_Hull": ([0.09, 0.08, 0.08, 1.0], 0.0, 0.75),
    "Paint_Band": ([0.80, 0.55, 0.16, 1.0], 0.0, 0.70),
    "Copper_Verdigris": ([0.18, 0.50, 0.42, 1.0], 0.5, 0.55),
    "Wood_Deck": ([0.46, 0.36, 0.25, 1.0], 0.0, 0.90),
    "Wood_Inside": ([0.07, 0.06, 0.05, 1.0], 0.0, 0.95),
    "Concretion": ([0.55, 0.38, 0.22, 1.0], 0.0, 1.00),
    "Iron_Rust": ([0.50, 0.22, 0.08, 1.0], 0.4, 0.80),
    # Deep Arctic life on hard surfaces: orange sponges, white anemones.
    "Sponge_Orange": ([0.95, 0.45, 0.12, 1.0], 0.0, 0.90),
    "Anemone_White": ([0.92, 0.88, 0.80, 1.0], 0.0, 0.60),
    "Sediment": ([0.50, 0.46, 0.38, 1.0], 0.0, 1.00),
}
parts = {name: [] for name in MATERIALS}

X_AXIS, Y_AXIS, Z_AXIS = [1, 0, 0], [0, 1, 0], [0, 0, 1]


def add(material_name, mesh):
    parts[material_name].append(mesh)


def rotation(axis, angle_degrees, point=None):
    return transformations.rotation_matrix(np.radians(angle_degrees), axis, point)


def translation(offset):
    return transformations.translation_matrix(offset)


# ---------- Hull ----------

def half_beam_at(z):
    """Half width of the hull at deck level. z from -L/2 (stern) to +L/2 (bow)."""
    position = z / (HULL_LENGTH / 2)  # -1 stern, +1 bow
    if position >= 0:
        return HULL_HALF_BEAM * (1 - position ** 2.2)  # fine bow
    return HULL_HALF_BEAM * (1 - 0.45 * (-position) ** 3)  # full stern with a flat transom


def sheer_at(z):
    """Deck height: higher at the bow and the stern."""
    position = z / (HULL_LENGTH / 2)
    return HULL_DEPTH + 0.6 * position ** 2 + 0.25 * max(position, 0)


def hull_grid():
    """Starboard side (x >= 0) of the hull: rows = stations (stern to bow), columns = keel to deck."""
    z_values = np.linspace(-HULL_LENGTH / 2, HULL_LENGTH / 2, STATIONS)
    grid = np.zeros((STATIONS, PROFILE_POINTS, 3))
    for station, z in enumerate(z_values):
        half_beam = half_beam_at(z)
        deck_height = sheer_at(z)
        for point in range(PROFILE_POINTS):
            t = point / (PROFILE_POINTS - 1)  # 0 = keel, 1 = deck edge
            # Rounded bilge: the side is wide quickly, then almost vertical.
            grid[station, point] = [half_beam * np.sin(t * np.pi / 2) ** 0.85, deck_height * t, z]
    return grid


def grid_faces(rows, columns, skip=None, keep_column=None):
    faces = []
    for row in range(rows - 1):
        for column in range(columns - 1):
            if skip and skip(row, column):
                continue
            if keep_column and not keep_column(column):
                continue
            a = row * columns + column
            b = a + 1
            c = a + columns
            d = c + 1
            faces += [[a, b, c], [b, d, c]]
    return faces


def orient_outwards(mesh, inward=False):
    """Turns each face so that it looks away from the hull centre line (or towards it)."""
    centres = mesh.triangles_center
    away = centres - np.stack([np.zeros(len(centres)), np.full(len(centres), HULL_DEPTH * 0.8),
                               centres[:, 2]], axis=1)
    wrong = np.einsum("ij,ij->i", mesh.face_normals, away) < 0
    if inward:
        wrong = ~wrong
    faces = mesh.faces.copy()
    faces[wrong] = faces[wrong][:, ::-1]
    return trimesh.Trimesh(mesh.vertices, faces, process=False)


def hull_frame(z, thickness=0.22):
    """One U-shaped frame at z, following the hull section, made of short straight pieces."""
    half_beam = half_beam_at(z) * 0.955  # between the outer planking and the inside lining
    deck_height = sheer_at(z) - 0.3
    t_values = np.linspace(0, 1, PROFILE_POINTS)
    starboard = [[half_beam * np.sin(t * np.pi / 2) ** 0.85, deck_height * t + 0.08, z] for t in t_values]
    points = np.array([[-x, y, z_] for x, y, z_ in starboard[::-1]] + starboard[1:])
    pieces = []
    for start, end in zip(points[:-1], points[1:]):
        length = np.linalg.norm(end - start)
        piece = trimesh.creation.box(extents=[thickness, thickness, length + thickness * 0.5])
        piece.apply_transform(trimesh.geometry.align_vectors([0, 0, 1], (end - start) / length))
        piece.apply_translation((start + end) / 2)
        pieces.append(piece)
    return trimesh.util.concatenate(pieces)


def hull():
    grid = hull_grid()

    # Hole in the starboard side, a bit behind the middle: broken planks (ragged top edge).
    hole_top_by_row = {row: 6 + int(RANDOM.random() > 0.6) for row in range(STATIONS)}

    def is_hole(row, column):
        z_fraction = row / (STATIONS - 1)
        return 0.36 < z_fraction < 0.5 and 3 <= column <= hole_top_by_row[row]

    # Colour bands by height on the side (column 0 = keel, last = deck edge).
    band_materials = (("Copper_Verdigris", lambda column: column <= 5),
                      ("Paint_Band", lambda column: column == PROFILE_POINTS - 3),
                      ("Wood_Hull", lambda column: 5 < column != PROFILE_POINTS - 3))

    for side in (1, -1):
        vertices = grid.reshape(-1, 3) * [side, 1, 1]
        skip = is_hole if side == 1 else None
        for material_name, keep_column in band_materials:
            band = trimesh.Trimesh(vertices, grid_faces(STATIONS, PROFILE_POINTS, skip, keep_column),
                                   process=False)
            add(material_name, orient_outwards(band))
        inner_vertices = vertices * [0.94, 1, 0.98] + [0, 0.15, 0]
        inner = trimesh.Trimesh(inner_vertices, grid_faces(STATIONS, PROFILE_POINTS, skip), process=False)
        add("Wood_Inside", orient_outwards(inner, inward=True))

    # Frames (ribs) seen through the hole: curved like the hull, from the keel up to the deck,
    # just inside the planking.
    for z in np.linspace(-HULL_LENGTH / 2 + 0.37 * HULL_LENGTH, -HULL_LENGTH / 2 + 0.49 * HULL_LENGTH, 6):
        add("Wood_Deck", hull_frame(z))
    # Flat transom at the stern.
    stern_z = -HULL_LENGTH / 2
    stern_profile = grid[0]
    transom_points = np.vstack([stern_profile, stern_profile[::-1] * [-1, 1, 1]])
    transom_points = np.vstack([transom_points * [1, 1, 0] + [0, 0, stern_z],
                                transom_points * [1, 1, 0] + [0, 0, stern_z - 0.2]])
    transom = trimesh.convex.convex_hull(transom_points)  # the stern section is convex
    add("Wood_Hull", transom)

    deck()
    add("Wood_Hull", keel())


def deck():
    """Deck planking, with a collapsed part in the middle (dark opening) and hatches."""
    vertices, faces = [], []
    z_values = np.linspace(-HULL_LENGTH / 2, HULL_LENGTH / 2, STATIONS)
    for z in z_values:
        half_beam = half_beam_at(z) * 0.97
        height = sheer_at(z) - 0.25
        vertices += [[-half_beam, height, z], [half_beam, height, z]]
    for station in range(STATIONS - 1):
        z_fraction = station / (STATIONS - 1)
        if 0.40 < z_fraction < 0.47:
            continue  # collapsed deck
        a = station * 2
        faces += [[a, a + 2, a + 1], [a + 1, a + 2, a + 3]]
    deck_mesh = trimesh.Trimesh(np.array(vertices), faces, process=False)
    if deck_mesh.face_normals[:, 1].mean() < 0:
        deck_mesh.invert()
    add("Wood_Deck", deck_mesh)
    for z in (-9.0, 8.0):
        hatch = trimesh.creation.box(extents=[2.2, 0.5, 2.2])
        hatch.apply_translation([0, sheer_at(z), z])
        add("Wood_Inside", hatch)


def keel():
    points = [[0, -0.5, -HULL_LENGTH / 2 - 0.3], [0, -0.5, HULL_LENGTH / 2 - 1.0],
              [0, sheer_at(HULL_LENGTH / 2) + 0.4, HULL_LENGTH / 2 + 0.6]]
    meshes = []
    for start, end in zip(points[:-1], points[1:]):
        start, end = np.array(start), np.array(end)
        length = np.linalg.norm(end - start)
        beam = trimesh.creation.box(extents=[0.35, 0.6, length])
        direction = (end - start) / length
        beam.apply_transform(trimesh.geometry.align_vectors([0, 0, 1], direction))
        beam.apply_translation((start + end) / 2)
        meshes.append(beam)
    return trimesh.util.concatenate(meshes)


# ---------- Masts, rigging remains, details ----------

def broken_mast(base, height, radius):
    mast = trimesh.creation.cylinder(radius=radius, height=height, sections=10)
    top = mast.vertices[:, 2] > 0
    # Splintered top: random heights on the top ring.
    mast.vertices[top, 2] += RANDOM.uniform(-0.6, 0.3, top.sum())
    mast.apply_transform(rotation(X_AXIS, -90))  # Z up -> Y up
    mast.apply_translation([base[0], base[1] + height / 2, base[2]])
    return mast


def masts():
    add("Wood_Deck", broken_mast([0, sheer_at(10.0), 10.0], 6.5, 0.32))
    add("Wood_Deck", broken_mast([0, sheer_at(0.5), 0.5], 11.0, 0.36))
    # Mizzen mast: snapped at the deck (it lies on the seabed, see fallen_mast).
    stump = broken_mast([0, sheer_at(-9.5), -9.5], 1.2, 0.3)
    add("Wood_Deck", stump)
    # Yard (horizontal spar) still hanging on the main mast, tilted.
    yard = trimesh.creation.cylinder(radius=0.15, height=11.0, sections=8)
    yard.apply_transform(rotation(Y_AXIS, 90))
    yard.apply_transform(rotation(Z_AXIS, 22))
    yard.apply_translation([0.5, sheer_at(0.5) + 8.0, 0.8])
    add("Wood_Deck", yard)
    # Bowsprit.
    bowsprit = trimesh.creation.cylinder(radius=0.22, height=7.0, sections=8)
    bowsprit.apply_transform(rotation(X_AXIS, 90 - 18))
    bowsprit.apply_translation([0, sheer_at(HULL_LENGTH / 2) + 1.2, HULL_LENGTH / 2 + 2.6])
    add("Wood_Deck", bowsprit)


def fallen_mast():
    """Mizzen mast lying across the seabed on the port side, half sunk."""
    fallen = trimesh.creation.cylinder(radius=0.28, height=17.0, sections=10)
    fallen.apply_transform(rotation(Y_AXIS, 90 + 28))
    fallen.apply_translation([-10.5, 0.1, -10.0])
    return fallen


def anchor():
    shank = trimesh.creation.box(extents=[0.2, 0.2, 2.4])
    arm = trimesh.creation.box(extents=[1.6, 0.2, 0.2])
    arm.apply_translation([0, 0, -1.1])
    ring = trimesh.creation.annulus(r_min=0.18, r_max=0.28, height=0.08, sections=12)
    ring.apply_transform(rotation(Y_AXIS, 90))
    ring.apply_translation([0, 0, 1.35])
    whole = trimesh.util.concatenate([shank, arm, ring])
    whole.apply_transform(rotation(Y_AXIS, 40))
    whole.apply_translation([3.8, 0.1, HULL_LENGTH / 2 + 1.5])
    return whole


def crust_lumps(count, area):
    """Mineral crust (concretion) and small growth on the wood, as flattened lumps."""
    (x_min, x_max), (y_min, y_max), (z_min, z_max) = area  # y: height above the deck
    for _ in range(count):
        lump = trimesh.creation.icosphere(subdivisions=1, radius=1.0)
        lump.apply_scale([RANDOM.uniform(0.3, 0.9), RANDOM.uniform(0.1, 0.25), RANDOM.uniform(0.3, 1.1)])
        z = RANDOM.uniform(z_min, z_max)
        x = RANDOM.uniform(x_min, x_max) * half_beam_at(z) / HULL_HALF_BEAM
        lump.apply_translation([x, sheer_at(z) - 0.25 + RANDOM.uniform(y_min, y_max), z])
        add("Concretion", lump)


def sea_life(count, area):
    """Orange sponges (round lumps) and white anemones (small stalk + crown) on the wreck."""
    (x_min, x_max), (y_min, y_max), (z_min, z_max) = area
    for _ in range(count):
        z = RANDOM.uniform(z_min, z_max)
        x = RANDOM.uniform(x_min, x_max) * half_beam_at(z) / HULL_HALF_BEAM
        position = [x, sheer_at(z) - 0.25 + RANDOM.uniform(y_min, y_max), z]
        if RANDOM.random() < 0.5:
            sponge = trimesh.creation.icosphere(subdivisions=1, radius=RANDOM.uniform(0.2, 0.45))
            sponge.apply_scale([1.0, RANDOM.uniform(0.7, 1.4), 1.0])
            sponge.apply_translation(position)
            add("Sponge_Orange", sponge)
        else:
            stalk = trimesh.creation.cylinder(radius=0.06, height=0.35, sections=6)
            stalk.apply_transform(rotation(X_AXIS, -90))
            stalk.apply_translation([position[0], position[1] + 0.17, position[2]])
            crown = trimesh.creation.cone(radius=0.22, height=0.18, sections=8)
            crown.apply_transform(rotation(X_AXIS, 90))  # opens upwards
            crown.apply_translation([position[0], position[1] + 0.5, position[2]])
            add("Anemone_White", trimesh.util.concatenate([stalk, crown]))


# ---------- The wreck ----------

def build_wreck():
    hull()
    masts()
    crust_lumps(40, ((-3.5, 3.5), (-0.1, 0.1), (-16, 16)))  # heights: above the deck
    sea_life(60, ((-3.2, 3.2), (0.0, 0.05), (-15, 15)))
    # Lean to port and sink into the sediment, all parts together.
    lean = translation([0, -1.2, 0]) @ rotation(Z_AXIS, 16) @ rotation(X_AXIS, -2)
    for meshes in parts.values():
        for mesh in meshes:
            mesh.apply_transform(lean)
    # The fallen mast and the anchor lie on the seabed, not on the ship.
    add("Wood_Deck", fallen_mast())
    add("Iron_Rust", anchor())

    # Sediment piled against the hull, on both sides.
    for z in np.linspace(-15, 15, 6):
        for side in (-1, 1):
            mound = trimesh.creation.icosphere(subdivisions=2, radius=1.0)
            mound.apply_scale([RANDOM.uniform(1.5, 2.5), RANDOM.uniform(0.3, 0.6), RANDOM.uniform(2.0, 3.5)])
            mound.apply_translation([side * (half_beam_at(z) + 0.4) - 0.8, -0.2, z])
            add("Sediment", mound)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("output_folder")
    parser.add_argument("--preview", help="also save a PNG preview")
    arguments = parser.parse_args()

    build_wreck()
    merged = {name: trimesh.util.concatenate(meshes) for name, meshes in parts.items() if meshes}
    output_path = Path(arguments.output_folder) / "ShipWreck.glb"
    write_glb(merged, output_path, MATERIALS, "ShipWreck")
    triangle_count = sum(len(mesh.faces) for mesh in merged.values())
    bounds = trimesh.util.concatenate(list(merged.values())).bounds
    print(f"{output_path}: {triangle_count} triangles, bounds {bounds.round(1).tolist()}")
    if arguments.preview:
        write_preview(merged, arguments.preview, MATERIALS,
                      limits=((-14, 14), (-21, 21), (-2, 18)), view=(22, -50))
        print(f"preview: {arguments.preview}")


if __name__ == "__main__":
    main()
