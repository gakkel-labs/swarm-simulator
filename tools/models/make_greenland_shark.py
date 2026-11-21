"""Builds the predator model used in the Unity scene: a Greenland shark.

The Greenland shark (Somniosus microcephalus) lives in the Arctic, down to more than 2,000 m,
so it is a believable predator near the Gakkel Ridge. Shape: about 4.5 m long, thick body,
short rounded snout, two small dorsal fins set far back, no anal fin, and a tail whose upper
lobe is only a little longer than the lower one. Low-poly, made from simple shapes.

Conventions: metres, Y up, +Z towards the snout (glTF convention, which is also Unity's forward),
pivot at the centre of the body.

Usage:
    pip install -r tools/models/requirements.txt
    python tools/models/make_greenland_shark.py unity-client/Assets/Models
    python tools/models/make_greenland_shark.py unity-client/Assets/Models --preview preview.png
"""
import argparse
from pathlib import Path

import numpy as np
import trimesh

from glb_export import write_glb, write_preview

MATERIALS = {
    # name: (base colour RGBA, metallic, roughness)
    "Skin_Back": ([0.20, 0.20, 0.19, 1.0], 0.0, 0.65),
    "Skin_Belly": ([0.42, 0.40, 0.37, 1.0], 0.0, 0.70),
    "Fin_Dark": ([0.13, 0.13, 0.13, 1.0], 0.0, 0.65),
    "Eye": ([0.55, 0.60, 0.55, 1.0], 0.2, 0.15),  # pale eyes (a parasite often clouds them)
}
parts = {name: [] for name in MATERIALS}

BODY_LENGTH = 3.7  # snout to tail base, the tail fin adds about 0.8 m
BODY_SECTIONS = 20
BODY_STATIONS = 28


def add(material_name, mesh):
    parts[material_name].append(mesh)


def body_shape(z):
    """Half-width, half-height and vertical centre of the body at z (0 = tail base, BODY_LENGTH = snout)."""
    position = z / BODY_LENGTH
    # Thick in the front half, narrowing to a slim tail base; blunt rounded snout.
    thickness = np.where(position > 0.85,
                         np.sqrt(np.clip(1 - ((position - 0.85) / 0.15) ** 2, 0, 1)),
                         0.25 + 0.75 * np.sin(np.clip(position / 0.85, 0, 1) * np.pi / 2) ** 0.8)
    half_width = 0.36 * thickness
    half_height = 0.40 * thickness
    centre_height = 0.04 * np.sin(position * np.pi)  # slight hump
    return half_width, half_height, centre_height


def body():
    stations = np.linspace(0.0, BODY_LENGTH, BODY_STATIONS)
    angles = np.linspace(0, 2 * np.pi, BODY_SECTIONS, endpoint=False)
    vertices = []
    for z in stations:
        half_width, half_height, centre_height = body_shape(z)
        half_width = max(half_width, 0.002)
        half_height = max(half_height, 0.002)
        # Flatter belly: the lower half of each ellipse is squashed a bit.
        y = np.where(np.sin(angles) < 0, 0.8, 1.0) * half_height * np.sin(angles) + centre_height
        x = half_width * np.cos(angles)
        vertices.append(np.stack([x, y, np.full_like(x, z)], axis=1))
    vertices = np.vstack(vertices)

    back_faces, belly_faces = [], []
    for station in range(BODY_STATIONS - 1):
        for section in range(BODY_SECTIONS):
            next_section = (section + 1) % BODY_SECTIONS
            a = station * BODY_SECTIONS + section
            b = station * BODY_SECTIONS + next_section
            c = a + BODY_SECTIONS
            d = b + BODY_SECTIONS
            faces = [[a, c, b], [b, c, d]]
            middle_angle = angles[section] + np.pi / BODY_SECTIONS
            (back_faces if np.sin(middle_angle) > -0.35 else belly_faces).extend(faces)

    # Close the tail base with a small cap (hidden by the tail fin).
    centre_index = len(vertices)
    vertices = np.vstack([vertices, [[0.0, body_shape(0.0)[2], 0.0]]])
    for section in range(BODY_SECTIONS):
        back_faces.append([centre_index, section, (section + 1) % BODY_SECTIONS])

    add("Skin_Back", trimesh.Trimesh(vertices, back_faces, process=True))
    add("Skin_Belly", trimesh.Trimesh(vertices, belly_faces, process=True))


def fin(points_2d, thickness, plane):
    """Thin fin from a 2D outline. plane 'yz' for vertical fins, 'xz' for flat ones."""
    outline = np.asarray(points_2d, dtype=float)
    front = []
    for first, second in outline:
        if plane == "yz":
            front.append([thickness / 2, first, second])
            front.append([-thickness / 2, first, second])
        else:
            front.append([first, thickness / 2, second])
            front.append([first, -thickness / 2, second])
    return trimesh.convex.convex_hull(np.array(front))


def fins():
    # Two small dorsal fins, set far back (typical of this shark).
    add("Fin_Dark", fin([(0.30, 1.55), (0.30, 1.20), (0.52, 1.28)], 0.04, "yz"))
    add("Fin_Dark", fin([(0.22, 0.95), (0.22, 0.68), (0.42, 0.74)], 0.04, "yz"))
    # Tail fin: upper lobe a bit longer than the lower one.
    add("Fin_Dark", fin([(0.05, 0.10), (0.62, -0.62), (0.45, -0.70), (0.0, -0.20)], 0.04, "yz"))
    add("Fin_Dark", fin([(-0.03, 0.10), (-0.48, -0.48), (-0.32, -0.56), (0.0, -0.20)], 0.04, "yz"))
    # Pectoral fins, angled down.
    for side in (-1, 1):
        pectoral = fin([(side * 0.25, 2.75), (side * 0.25, 2.45), (side * 0.70, 2.35), (side * 0.62, 2.55)],
                       0.03, "xz")
        pectoral.apply_transform(trimesh.transformations.rotation_matrix(
            np.radians(-side * 18), [0, 0, 1], [side * 0.25, -0.15, 2.6]))
        pectoral.apply_translation([0, -0.18, 0])
        add("Fin_Dark", pectoral)
        # Small pelvic fins.
        pelvic = fin([(side * 0.15, 1.45), (side * 0.15, 1.25), (side * 0.36, 1.18)], 0.03, "xz")
        pelvic.apply_translation([0, -0.24, 0])
        add("Fin_Dark", pelvic)


def eyes():
    for side in (-1, 1):
        half_width, half_height, centre_height = body_shape(3.45)
        eye = trimesh.creation.icosphere(subdivisions=1, radius=0.035)
        eye.apply_translation([side * half_width * 0.92, centre_height + half_height * 0.35, 3.45])
        add("Eye", eye)


def build_shark():
    body()
    fins()
    eyes()
    # Pivot at the centre of the whole fish (tail fin included).
    for meshes in parts.values():
        for mesh in meshes:
            mesh.apply_translation([0, 0, -(BODY_LENGTH - 0.7) / 2])


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("output_folder")
    parser.add_argument("--preview", help="also save a PNG preview")
    arguments = parser.parse_args()

    build_shark()
    merged = {name: trimesh.util.concatenate(meshes) for name, meshes in parts.items() if meshes}
    output_path = Path(arguments.output_folder) / "GreenlandShark.glb"
    write_glb(merged, output_path, MATERIALS, "GreenlandShark")
    triangle_count = sum(len(mesh.faces) for mesh in merged.values())
    bounds = trimesh.util.concatenate(list(merged.values())).bounds
    print(f"{output_path}: {triangle_count} triangles, bounds {bounds.round(2).tolist()}")
    if arguments.preview:
        write_preview(merged, arguments.preview, MATERIALS,
                      limits=((-1.5, 1.5), (-2.5, 2.5), (-1.0, 1.0)), view=(18, -65))
        print(f"preview: {arguments.preview}")


if __name__ == "__main__":
    main()
