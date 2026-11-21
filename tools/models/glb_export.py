"""Shared helpers for the model scripts: GLB export and a quick preview picture."""
import json
import struct

import numpy as np


def shaded_arrays(mesh):
    """Smooth normals on curved surfaces, hard edges where faces meet at a sharp angle."""
    shaded = mesh.smooth_shaded if len(mesh.faces) > 0 else mesh
    vertices = shaded.vertices.astype(np.float32)
    normals = shaded.vertex_normals.astype(np.float32)
    faces = shaded.faces.astype(np.uint32)
    return vertices, normals, faces


TEXTURE_METRES_PER_REPEAT = 2.0


def box_projected_uvs(vertices, normals):
    """Texture coordinates for any texture, without unwrapping: each vertex takes the plane
    its normal faces most (top, side or front), and 1 texture repeat = 2 m.
    On a flat deck (normal up) U goes across the ship and V along it, so planks run bow to stern."""
    absolute = np.abs(normals)
    uvs = np.where((absolute[:, 1] >= absolute[:, 0]) & (absolute[:, 1] >= absolute[:, 2]),
                   0, np.where(absolute[:, 0] >= absolute[:, 2], 1, 2))
    projected = np.empty((len(vertices), 2), dtype=np.float32)
    top, side, front = uvs == 0, uvs == 1, uvs == 2
    projected[top] = vertices[top][:, [0, 2]]
    projected[side] = vertices[side][:, [2, 1]]
    projected[front] = vertices[front][:, [0, 1]]
    return (projected / TEXTURE_METRES_PER_REPEAT).astype(np.float32)


def write_glb(merged, path, materials_by_name, model_name):
    """merged: {material name: trimesh}. One mesh, one primitive per material (good for Unity)."""
    binary = bytearray()
    buffer_views, accessors, primitives, materials = [], [], [], []

    def push(array, target, component_type, accessor_type, with_bounds=False):
        while len(binary) % 4:
            binary.append(0)
        offset = len(binary)
        binary.extend(array.tobytes())
        buffer_views.append({"buffer": 0, "byteOffset": offset, "byteLength": array.nbytes, "target": target})
        accessor = {"bufferView": len(buffer_views) - 1, "componentType": component_type,
                    "count": int(array.shape[0]) if accessor_type != "SCALAR" else int(array.size),
                    "type": accessor_type}
        if with_bounds:
            accessor["min"] = array.min(axis=0).tolist()
            accessor["max"] = array.max(axis=0).tolist()
        accessors.append(accessor)
        return len(accessors) - 1

    for material_index, (name, mesh) in enumerate(merged.items()):
        vertices, normals, faces = shaded_arrays(mesh)
        position_accessor = push(vertices, 34962, 5126, "VEC3", True)
        normal_accessor = push(normals, 34962, 5126, "VEC3")
        uv_accessor = push(box_projected_uvs(vertices, normals), 34962, 5126, "VEC2")
        index_accessor = push(faces.reshape(-1), 34963, 5125, "SCALAR")
        primitives.append({"attributes": {"POSITION": position_accessor, "NORMAL": normal_accessor,
                                          "TEXCOORD_0": uv_accessor},
                           "indices": index_accessor, "material": material_index})
        colour, metallic, roughness = materials_by_name[name]
        materials.append({"name": name, "pbrMetallicRoughness": {
            "baseColorFactor": colour, "metallicFactor": metallic, "roughnessFactor": roughness}})

    gltf = {"asset": {"version": "2.0", "generator": "tools/models"},
            "scene": 0, "scenes": [{"nodes": [0]}],
            "nodes": [{"name": model_name, "mesh": 0}],
            "meshes": [{"name": model_name, "primitives": primitives}],
            "materials": materials, "accessors": accessors, "bufferViews": buffer_views,
            "buffers": [{"byteLength": len(binary)}]}
    json_chunk = json.dumps(gltf, separators=(",", ":")).encode()
    json_chunk += b" " * (-len(json_chunk) % 4)
    while len(binary) % 4:
        binary.append(0)
    total_length = 12 + 8 + len(json_chunk) + 8 + len(binary)
    with open(path, "wb") as glb_file:
        glb_file.write(struct.pack("<III", 0x46546C67, 2, total_length))
        glb_file.write(struct.pack("<II", len(json_chunk), 0x4E4F534A) + json_chunk)
        glb_file.write(struct.pack("<II", len(binary), 0x004E4942) + binary)


def write_preview(merged, path, materials_by_name, limits, view, zoom=1.6):
    """Quick shaded picture, to check a model without opening Unity.
    limits: ((x min, x max), (z min, z max), (y min, y max)) in model metres; view: (elevation, azimuth)."""
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    from mpl_toolkits.mplot3d.art3d import Poly3DCollection

    figure = plt.figure(figsize=(12, 7), dpi=110)
    axes = figure.add_subplot(projection="3d")
    light = np.array([0.4, 0.8, 0.3])
    light /= np.linalg.norm(light)
    # One collection for all faces, so that matplotlib sorts faces by depth across materials.
    all_triangles, all_colours = [], []
    for name, mesh in merged.items():
        colour = np.array(materials_by_name[name][0][:3])
        shading = 0.35 + 0.65 * np.clip(mesh.face_normals @ light, 0, 1)
        # Matplotlib axes: X east, Y north (= glTF Z), Z up (= glTF Y).
        all_triangles.append(mesh.vertices[mesh.faces][:, :, [0, 2, 1]])
        all_colours.append(np.clip(colour * shading[:, None] * 1.4, 0, 1))
    axes.add_collection3d(Poly3DCollection(np.concatenate(all_triangles), facecolors=np.concatenate(all_colours),
                                           linewidths=0))
    axes.set_facecolor("#1d3442")
    (x_min, x_max), (z_min, z_max), (y_min, y_max) = limits
    axes.set_xlim(x_min, x_max)
    axes.set_ylim(z_min, z_max)
    axes.set_zlim(y_min, y_max)
    axes.set_box_aspect((x_max - x_min, z_max - z_min, y_max - y_min), zoom=zoom)
    axes.view_init(elev=view[0], azim=view[1])
    axes.set_axis_off()
    figure.savefig(path, bbox_inches="tight", facecolor="#1d3442")
