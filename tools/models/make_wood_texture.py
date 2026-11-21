"""Makes the default deck texture: old wooden planks, dark and worn after a long time under water.

Output: unity-client/Assets/Models/WoodPlanks.png (512 x 512, tiles in both directions).
The models use 1 texture repeat = 2 m (see glb_export.TEXTURE_METRES_PER_REPEAT),
so 8 planks across the picture = planks about 25 cm wide.
Planks run along V (top to bottom of the picture), which is bow to stern on the deck.

Run: python tools/models/make_wood_texture.py
"""
from pathlib import Path

import numpy as np
from matplotlib import image as matplotlib_image

TEXTURE_SIZE = 512
PLANK_COUNT = 8
PLANK_SEGMENTS = 2          # each plank column has 2 boards, with an end joint between them
SEAM_WIDTH_PIXELS = 3
OUTPUT_PATH = Path(__file__).resolve().parents[2] / "unity-client/Assets/Models/WoodPlanks.png"

WOOD_BASE_COLOUR = np.array([0.36, 0.27, 0.19])   # dark oak
SEAM_COLOUR = np.array([0.10, 0.08, 0.06])


def periodic_noise(random, size, cells):
    """Smooth noise that tiles: sum of a few sine waves with whole periods over the picture."""
    coordinates = np.arange(size) / size * 2 * np.pi
    u, v = np.meshgrid(coordinates, coordinates)
    noise = np.zeros((size, size))
    for _ in range(cells):
        frequency_u, frequency_v = random.integers(1, 6), random.integers(1, 12)
        phase = random.uniform(0, 2 * np.pi)
        noise += np.sin(frequency_u * u + frequency_v * v + phase) / cells
    return noise


def main():
    random = np.random.default_rng(1881)
    plank_width = TEXTURE_SIZE // PLANK_COUNT
    board_length = TEXTURE_SIZE // PLANK_SEGMENTS
    rows, columns = np.mgrid[0:TEXTURE_SIZE, 0:TEXTURE_SIZE]
    plank_index = columns // plank_width
    # Joints are shifted from one plank to the next, like a real deck.
    joint_offsets = random.integers(0, board_length, PLANK_COUNT)
    shifted_rows = (rows + joint_offsets[plank_index]) % TEXTURE_SIZE
    board_index = plank_index * PLANK_SEGMENTS + shifted_rows // board_length

    # Each board has its own shade, a little lighter or darker.
    board_shade = random.uniform(0.75, 1.2, PLANK_COUNT * PLANK_SEGMENTS)[board_index]

    # Grain: thin lines along the plank, slightly wavy.
    position_in_plank = (columns % plank_width) / plank_width
    waviness = 0.08 * periodic_noise(random, TEXTURE_SIZE, 6)
    grain_phase = random.uniform(0, 10, PLANK_COUNT * PLANK_SEGMENTS)[board_index]
    grain = 0.5 + 0.5 * np.sin((position_in_plank + waviness) * 2 * np.pi * 7 + grain_phase)
    grain = 0.88 + 0.12 * grain ** 3

    # Fine fibres and large stains (sediment, worn spots).
    # One random value per pixel column, so the fibres run along the plank and the picture still tiles.
    fibres = (1 + 0.05 * random.standard_normal(TEXTURE_SIZE))[None, :]
    stains = 0.9 + 0.15 * periodic_noise(random, TEXTURE_SIZE, 10)

    brightness = board_shade * grain * fibres * stains
    colours = WOOD_BASE_COLOUR[None, None, :] * brightness[:, :, None]

    # Dark gaps between planks and at the end joints.
    is_side_seam = (columns % plank_width) < SEAM_WIDTH_PIXELS
    is_end_joint = (shifted_rows % board_length) < SEAM_WIDTH_PIXELS
    colours[is_side_seam | is_end_joint] = SEAM_COLOUR

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    matplotlib_image.imsave(OUTPUT_PATH, np.clip(colours, 0, 1))
    print(f"Wrote {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
