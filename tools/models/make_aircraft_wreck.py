"""Builds the aircraft wreck model used as the search target in the Unity scene.

The aircraft is a generic twin-engine airliner (about 32 m long), not a real model.
It lies on the seabed in three main pieces: the front section with the left wing,
the rear section with the tail, and the outer part of the right wing. An engine
and small panels lie around them, and sediment has piled up against the hull.

Conventions: metres, Y up, +Z towards the nose (glTF convention, which is also
Unity's forward), pivot on the seabed (y = 0) at the centre of the wreck.
Parts below y = 0 are buried in the sediment.

Output: GLB with one mesh and one primitive per material (good for Unity).

Usage:
    pip install -r tools/models/requirements.txt
    python tools/models/make_aircraft_wreck.py unity-client/Assets/Models
    python tools/models/make_aircraft_wreck.py unity-client/Assets/Models --preview preview.png
"""
import argparse
from pathlib import Path

import numpy as np
import trimesh
from trimesh import transformations

from glb_export import write_glb, write_preview

RANDOM = np.random.default_rng(75)  # fixed seed: same wreck every time

FUSELAGE_RADIUS = 1.9
HULL_THICKNESS = 0.12
FUSELAGE_SECTIONS = 40

MATERIALS = {
    # name: (base colour RGBA, metallic, roughness)
    "Hull_White": ([0.72, 0.72, 0.68, 1.0], 0.1, 0.75),
    "Hull_Stripe": ([0.10, 0.22, 0.40, 1.0], 0.1, 0.70),
    "Interior_Dark": ([0.06, 0.06, 0.06, 1.0], 0.0, 0.90),
    "Window_Dark": ([0.02, 0.03, 0.04, 1.0], 0.3, 0.25),
    "Engine_Metal": ([0.42, 0.43, 0.45, 1.0], 0.7, 0.45),
    "Sediment": ([0.50, 0.46, 0.38, 1.0], 0.0, 1.00),
}
parts = {name: [] for name in MATERIALS}


def add(material_name, mesh):
    parts[material_name].append(mesh)


def rotation(axis, angle_degrees, point=None):
    return transformations.rotation_matrix(np.radians(angle_degrees), axis, point)


def translation(offset):
    return transformations.translation_matrix(offset)


X_AXIS, Y_AXIS, Z_AXIS = [1, 0, 0], [0, 1, 0], [0, 0, 1]
MIRROR_X = np.diag([-1.0, 1.0, 1.0, 1.0])  # left side = mirror of the right side


# ---------- Shape helpers ----------

def jag_profile(sections, amplitude):
    """Random but smooth offsets around a ring, for torn metal edges."""
    raw = RANDOM.uniform(-1.0, 1.0, sections)
    smooth = (raw + np.roll(raw, 1) + np.roll(raw, -1)) / 3.0
    return smooth * amplitude + RANDOM.uniform(0, amplitude, sections) * (RANDOM.random(sections) > 0.7)


def ring(radius, z_positions, sections):
    angles = np.linspace(0, 2 * np.pi, sections, endpoint=False)
    return np.stack([radius * np.cos(angles), radius * np.sin(angles), z_positions], axis=1)


def tube_between(rings_start, rings_end, flip=False):
    """Quads between two rings of the same size."""
    sections = len(rings_start)
    vertices = np.vstack([rings_start, rings_end])
    faces = []
    for index in range(sections):
        next_index = (index + 1) % sections
        a, b = index, next_index
        c, d = next_index + sections, index + sections
        faces += [[a, b, c], [a, c, d]] if not flip else [[a, c, b], [a, d, c]]
    return trimesh.Trimesh(vertices, faces, process=False)


def broken_fuselage(z_start, z_end, jag_start=0.0, jag_end=0.0):
    """Open hull tube along Z: white outside, dark inside, dark torn rims."""
    sections = FUSELAGE_SECTIONS
    start_offsets = jag_profile(sections, jag_start) if jag_start else np.zeros(sections)
    end_offsets = -jag_profile(sections, jag_end) if jag_end else np.zeros(sections)
    inner_radius = FUSELAGE_RADIUS - HULL_THICKNESS

    outer_start = ring(FUSELAGE_RADIUS, z_start + start_offsets, sections)
    outer_end = ring(FUSELAGE_RADIUS, z_end + end_offsets, sections)
    inner_start = ring(inner_radius, z_start + start_offsets, sections)
    inner_end = ring(inner_radius, z_end + end_offsets, sections)

    add("Hull_White", tube_between(outer_start, outer_end))
    add("Interior_Dark", tube_between(inner_start, inner_end, flip=True))
    if jag_start:
        add("Interior_Dark", tube_between(inner_start, outer_start))
    if jag_end:
        add("Interior_Dark", tube_between(outer_end, inner_end))

    # Blue cheatline below the windows, on both sides.
    for side in (-1, 1):
        stripe = trimesh.creation.box(extents=[0.03, 0.22, (z_end - z_start) - max(jag_start, jag_end) * 2])
        stripe.apply_translation([side * (FUSELAGE_RADIUS + 0.005), 0.25, (z_start + z_end) / 2])
        add("Hull_Stripe", stripe)
    cabin_windows(z_start + jag_start + 0.6, z_end - jag_end - 0.6)


def cabin_windows(z_from, z_to, spacing=0.55):
    angle = np.radians(14)  # just above the middle of the hull side
    for z in np.arange(z_from, z_to, spacing):
        for side in (-1, 1):
            window = trimesh.creation.box(extents=[0.02, 0.34, 0.24])
            window.apply_transform(rotation(Z_AXIS, side * np.degrees(angle)))
            x = side * (FUSELAGE_RADIUS + 0.01) * np.cos(angle)
            window.apply_translation([x, (FUSELAGE_RADIUS + 0.01) * np.sin(angle), z])
            add("Window_Dark", window)


def revolve_along_z(profile, sections=FUSELAGE_SECTIONS):
    """profile: list of (radius, z). Revolves around the Z axis."""
    profile = np.asarray(profile, dtype=float)
    # trimesh.creation.revolve turns (radius, height) around Z.
    return trimesh.creation.revolve(profile, sections=sections)


def nose_cone(z_base):
    t = np.linspace(0, 1, 9)
    radii = FUSELAGE_RADIUS * np.sqrt(1 - t ** 2.2)
    heights = z_base + 4.2 * t
    profile = list(zip(radii, heights)) + [(0.0, z_base + 4.2)]
    nose = revolve_along_z(profile)
    nose.apply_translation([0, 0, 0])
    nose.vertices[:, 1] -= 0.45 * (nose.vertices[:, 2] - z_base) / 4.2 * (nose.vertices[:, 1] < 0.5)
    add("Hull_White", nose)
    # Cockpit windows: a band of dark panels on the upper front.
    for index, x in enumerate(np.linspace(-0.9, 0.9, 6)):
        pane = trimesh.creation.box(extents=[0.32, 0.03, 0.42])
        pane.apply_transform(rotation(X_AXIS, -32))
        pane.apply_translation([x, FUSELAGE_RADIUS * 0.72 - abs(x) * 0.25, z_base + 1.45])
        add("Window_Dark", pane)


def tail_cone(z_base):
    t = np.linspace(0, 1, 9)
    radii = FUSELAGE_RADIUS * (1 - 0.85 * t ** 1.3)
    heights = z_base - 7.0 * t
    profile = [(0.0, z_base)] + list(zip(radii, heights)) + [(0.0, z_base - 7.0)]
    cone = revolve_along_z(profile)
    # Upswept tail: lift the rear.
    lift = np.clip((z_base - cone.vertices[:, 2]) / 7.0, 0, 1) ** 1.5 * 1.2
    cone.vertices[:, 1] += lift
    add("Hull_White", cone)


def tapered_panel(root_chord, tip_chord, span, sweep_degrees, root_thickness, tip_thickness):
    """Wing-like panel along +X from the root, chord along Z, convex hull of 8 points."""
    sweep = span * np.tan(np.radians(sweep_degrees))
    points = []
    for x, chord, thickness, z_shift in ((0.0, root_chord, root_thickness, 0.0),
                                         (span, tip_chord, tip_thickness, -sweep)):
        for z in (z_shift + chord / 2, z_shift - chord / 2):
            for y in (thickness / 2, -thickness / 2):
                points.append([x, y, z])
    return trimesh.convex.convex_hull(np.array(points))


def engine():
    """Turbofan nacelle along Z, centred on the origin, with a dark intake and fan."""
    nacelle = revolve_along_z([(0.0, -1.9), (0.55, -1.9), (0.95, -1.2), (1.0, 0.6), (0.95, 1.6), (0.0, 1.6)],
                              sections=24)
    meshes = [("Engine_Metal", nacelle)]
    intake = trimesh.creation.cylinder(radius=0.82, height=0.05, sections=24)
    intake.apply_translation([0, 0, 1.62])
    meshes.append(("Interior_Dark", intake))
    for blade_index in range(10):
        blade = trimesh.creation.box(extents=[0.75, 0.05, 0.12])
        blade.apply_translation([0.4, 0, 0])
        blade.apply_transform(rotation(X_AXIS, 25))
        blade.apply_transform(rotation(Z_AXIS, blade_index * 36))
        blade.apply_translation([0, 0, 1.5])
        meshes.append(("Engine_Metal", blade))
    return meshes


# ---------- The wreck ----------

def collect_group(build):
    """Runs build() and returns the meshes it added, removing them from parts."""
    before = {name: len(meshes) for name, meshes in parts.items()}
    build()
    group = []
    for name, meshes in parts.items():
        group += [(name, mesh) for mesh in meshes[before[name]:]]
        del meshes[before[name]:]
    return group


def place_group(group, matrix):
    for name, mesh in group:
        mesh = mesh.copy()
        mesh.apply_transform(matrix)
        add(name, mesh)


def front_section():
    broken_fuselage(0.0, 13.0, jag_start=0.9)
    nose_cone(13.0)
    # Left wing, whole, slightly bent down at the tip.
    left_wing = tapered_panel(5.5, 1.6, 13.5, 27, 0.55, 0.15)
    left_wing.vertices[:, 1] -= 0.03 * left_wing.vertices[:, 0] ** 1.4
    left_wing.apply_transform(MIRROR_X)  # point it to -X
    left_wing.apply_translation([-FUSELAGE_RADIUS * 0.8, -1.1, 4.0])
    add("Hull_White", left_wing)
    for name, mesh in engine():
        mesh.apply_translation([-5.2, -1.9, 5.8])
        add(name, mesh)
    pylon = trimesh.creation.box(extents=[0.3, 0.8, 2.4])
    pylon.apply_translation([-5.2, -1.25, 5.0])
    add("Engine_Metal", pylon)
    # Right wing stub, torn off after 3.5 m.
    stub = tapered_panel(5.5, 4.6, 3.5, 27, 0.55, 0.48)
    stub.apply_translation([FUSELAGE_RADIUS * 0.8, -1.1, 4.0])
    add("Hull_White", stub)


def rear_section():
    broken_fuselage(-9.0, 0.0, jag_end=1.1)
    tail_cone(-9.0)
    fin = tapered_panel(5.0, 2.0, 6.0, 38, 0.4, 0.15)
    fin.apply_transform(rotation(Z_AXIS, 90))  # span along +Y
    fin.apply_translation([0, FUSELAGE_RADIUS * 0.7, -11.8])
    add("Hull_White", fin)
    left_stabiliser = tapered_panel(3.0, 1.2, 5.0, 32, 0.25, 0.1)
    left_stabiliser.apply_transform(MIRROR_X)
    left_stabiliser.apply_translation([-0.6, 0.9, -13.0])
    add("Hull_White", left_stabiliser)
    # The right stabiliser broke off: only a short stub remains.
    stub = tapered_panel(3.0, 2.4, 1.2, 32, 0.25, 0.22)
    stub.apply_translation([0.6, 0.9, -13.0])
    add("Hull_White", stub)


def outer_right_wing():
    wing = tapered_panel(4.6, 1.6, 10.0, 27, 0.48, 0.15)
    add("Hull_White", wing)
    # Torn root: a dark strip.
    torn = trimesh.creation.box(extents=[0.08, 0.5, 4.4])
    torn.apply_translation([0.0, 0.0, 0.0])
    add("Interior_Dark", torn)


def build_wreck():
    centre_height = FUSELAGE_RADIUS - 0.7  # sunk 0.7 m into the sediment

    front = collect_group(front_section)
    place_group(front, translation([1.5, centre_height, 3.0]) @ rotation(Y_AXIS, -6) @ rotation(Z_AXIS, 7))

    rear = collect_group(rear_section)
    place_group(rear, translation([-2.0, centre_height - 0.15, -1.5]) @ rotation(Y_AXIS, 17) @ rotation(Z_AXIS, -12))

    right_wing = collect_group(outer_right_wing)
    place_group(right_wing, translation([9.5, -0.05, -2.0]) @ rotation(Y_AXIS, 48) @ rotation(Z_AXIS, 7))

    loose_engine = collect_group(lambda: [add(name, mesh) for name, mesh in engine()])
    place_group(loose_engine, translation([8.0, 0.75, 9.0]) @ rotation(Y_AXIS, 115) @ rotation(Z_AXIS, 25))

    # Bent panels scattered around.
    for _ in range(14):
        panel = trimesh.creation.box(extents=[RANDOM.uniform(0.6, 2.0), 0.05, RANDOM.uniform(0.4, 1.4)])
        panel.vertices[:, 1] += 0.15 * panel.vertices[:, 0] ** 2
        angle = RANDOM.uniform(0, 2 * np.pi)
        distance = RANDOM.uniform(4, 15)
        matrix = (translation([distance * np.cos(angle), 0.05, distance * np.sin(angle)])
                  @ rotation(Y_AXIS, RANDOM.uniform(0, 360)) @ rotation(X_AXIS, RANDOM.uniform(-15, 15)))
        panel.apply_transform(matrix)
        add("Hull_White" if RANDOM.random() > 0.3 else "Engine_Metal", panel)

    # Sediment piled against the hull, and buried parts hidden under small mounds.
    for z, x in ((-6, -3.0), (-2, -0.6), (2, 4.0), (7, 3.9), (11, 3.5), (-9, -1.0), (5, -0.5)):
        mound = trimesh.creation.icosphere(subdivisions=2, radius=1.0)
        mound.apply_scale([RANDOM.uniform(1.6, 2.6), RANDOM.uniform(0.35, 0.6), RANDOM.uniform(2.0, 3.5)])
        mound.apply_translation([x, -0.1, z])
        add("Sediment", mound)


# ---------- Export ----------

def merged_parts():
    merged = {}
    for name, meshes in parts.items():
        if meshes:
            merged[name] = trimesh.util.concatenate(meshes)
    return merged


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("output_folder")
    parser.add_argument("--preview", help="also save a PNG preview")
    arguments = parser.parse_args()

    build_wreck()
    merged = merged_parts()
    output_path = Path(arguments.output_folder) / "AircraftWreck.glb"
    write_glb(merged, output_path, MATERIALS, "AircraftWreck")
    triangle_count = sum(len(mesh.faces) for mesh in merged.values())
    bounds = trimesh.util.concatenate(list(merged.values())).bounds
    print(f"{output_path}: {triangle_count} triangles, bounds {bounds.round(1).tolist()}")
    if arguments.preview:
        write_preview(merged, arguments.preview, MATERIALS,
                      limits=((-13, 13), (-17, 17), (-1, 8)), view=(24, -55))
        print(f"preview: {arguments.preview}")


if __name__ == "__main__":
    main()
