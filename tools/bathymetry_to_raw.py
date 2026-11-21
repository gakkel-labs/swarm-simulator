"""Convert a bathymetry GeoTIFF into a 16-bit RAW heightmap for a Unity Terrain.

Works with any projection the GeoTIFF declares, for example:
- GEBCO global grid: latitude / longitude (EPSG:4326), 15 arc-second cells. Near the pole a
  cell is about 57 m wide but 463 m tall;
- GEBCO "North Polar" grid = IBCAO 5.2: polar stereographic (EPSG:3996), square 100 m cells,
  but the grid is rotated compared with true north (by the longitude, about 6 degrees here).

The script cuts a square measured in real metres, centred on a point (Aurora by default) and
aligned with true north: it uses a local "azimuthal equidistant" projection centred on that
point, converts every target point to the projection of the file (PROJ, through rasterio)
and reads the depth there with a cubic spline. Then it normalises the depths to 0..65535
and writes a little-endian RAW file that Unity can load.

Usage:
    python tools/bathymetry_to_raw.py [input.tif] [--output out.bytes] [--size 513] [--side-km 20]
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import rasterio
from rasterio.crs import CRS
from rasterio.warp import transform as transform_points
from scipy.ndimage import map_coordinates

UINT16_MAX = 65535
TOOLS_DIRECTORY = Path(__file__).resolve().parent
DEFAULT_INPUT = (
    TOOLS_DIRECTORY / "data" / "north_polar_2026_-763160.0_-789160.0_-98072.0_-72072.0_geotiff.tif"
)
# Unity loads it with Resources.Load<TextAsset>, which needs the .bytes extension.
UNITY_RESOURCES_DIRECTORY = TOOLS_DIRECTORY.parent / "unity-client" / "Assets" / "Resources"
DEFAULT_OUTPUT = UNITY_RESOURCES_DIRECTORY / "Seabed" / "aurora_heightmap_513.bytes"
DEFAULT_SIDE_METRES = 20_000


@dataclass(frozen=True)
class GeoPoint:
    """A point on the Earth, in degrees (WGS 84)."""

    latitude: float
    longitude: float


# Aurora hydrothermal field, Gakkel Ridge (82°53.8' N, 6°15.3' W).
AURORA = GeoPoint(latitude=82.897, longitude=-6.255)


@dataclass(frozen=True)
class ConversionSummary:
    min_depth_metres: float
    max_depth_metres: float
    centre_depth_metres: float
    side_metres: float
    metres_per_pixel: float

    @property
    def relief_metres(self) -> float:
        return self.max_depth_metres - self.min_depth_metres


def local_projection(centre: GeoPoint) -> CRS:
    """Metres east / north of the centre, with true north up (azimuthal equidistant)."""
    return CRS.from_proj4(
        f"+proj=aeqd +lat_0={centre.latitude} +lon_0={centre.longitude} +datum=WGS84 +units=m"
    )


def sample_square(
    elevations: np.ndarray,
    grid_transform: rasterio.Affine,
    grid_crs: CRS,
    centre: GeoPoint,
    side_metres: float,
    size: int,
) -> np.ndarray:
    """Read a size x size square of side_metres, centred on centre and aligned with true north.

    Output rows go south to north and columns west to east (Unity Terrain order:
    row 0 is at z = 0).
    """
    offsets_metres = np.linspace(-side_metres / 2, side_metres / 2, size)
    eastings, northings = np.meshgrid(offsets_metres, offsets_metres)  # rows = north offset

    grid_xs, grid_ys = transform_points(
        local_projection(centre), grid_crs, eastings.ravel(), northings.ravel()
    )
    columns, rows = ~grid_transform @ (np.asarray(grid_xs), np.asarray(grid_ys))

    row_count, column_count = elevations.shape
    inside = (rows >= 0) & (rows <= row_count) & (columns >= 0) & (columns <= column_count)
    if not inside.all():
        raise ValueError(
            f"A {side_metres / 1000:.1f} km square around {centre} goes outside the data."
        )

    # Values sit at the centre of each cell, hence - 0.5. Cubic spline: a smooth curve through
    # the data points; a bilinear interpolation would leave kinks (visible stripes in Unity).
    row_positions = np.clip(rows - 0.5, 0, row_count - 1)
    column_positions = np.clip(columns - 0.5, 0, column_count - 1)
    sampled = map_coordinates(
        elevations, [row_positions, column_positions], order=3, mode="nearest"
    )
    return sampled.reshape(size, size)


def normalize_to_uint16(elevations: np.ndarray) -> tuple[np.ndarray, float, float]:
    """Map the deepest point to 0 and the shallowest to 65535."""
    min_elevation = float(elevations.min())
    max_elevation = float(elevations.max())
    if max_elevation == min_elevation:
        raise ValueError("The area is perfectly flat: nothing to normalise.")
    scaled = (elevations - min_elevation) / (max_elevation - min_elevation) * UINT16_MAX
    return np.round(scaled).astype(np.uint16), min_elevation, max_elevation


def write_raw(heights: np.ndarray, output_path: Path) -> None:
    """Write 16-bit unsigned values, little-endian ('Windows' byte order in Unity)."""
    output_path.parent.mkdir(parents=True, exist_ok=True)
    heights.astype("<u2").tofile(output_path)


def convert(
    input_path: Path,
    output_path: Path,
    size: int = 513,
    side_metres: float = DEFAULT_SIDE_METRES,
    centre: GeoPoint = AURORA,
) -> ConversionSummary:
    with rasterio.open(input_path) as dataset:
        band = dataset.read(1, masked=True)
        if np.ma.is_masked(band) and band.mask.any():
            raise ValueError(f"{input_path} contains 'no data' cells.")
        elevations = band.filled().astype(np.float64)
        grid_transform, grid_crs = dataset.transform, dataset.crs

    square = sample_square(elevations, grid_transform, grid_crs, centre, side_metres, size)
    heights, min_elevation, max_elevation = normalize_to_uint16(square)
    write_raw(heights, output_path)

    return ConversionSummary(
        min_depth_metres=-max_elevation,
        max_depth_metres=-min_elevation,
        centre_depth_metres=-float(square[size // 2, size // 2]),
        side_metres=side_metres,
        metres_per_pixel=side_metres / (size - 1),
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("input", nargs="?", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--size", type=int, default=513, help="2^n + 1 (Unity Terrain)")
    parser.add_argument("--side-km", type=float, default=DEFAULT_SIDE_METRES / 1000)
    arguments = parser.parse_args()

    summary = convert(
        arguments.input, arguments.output, arguments.size, arguments.side_km * 1000
    )

    print(f"Heightmap written : {arguments.output} ({arguments.size} x {arguments.size})")
    print(f"Centre            : Aurora {AURORA.latitude} N, {AURORA.longitude} E")
    print(f"Depth min / max   : {summary.min_depth_metres:.0f} m / {summary.max_depth_metres:.0f} m")
    print(f"Depth at centre   : {summary.centre_depth_metres:.0f} m")
    print(f"Relief            : {summary.relief_metres:.0f} m  -> Unity Terrain Height (x exaggeration)")
    print(f"Square side       : {summary.side_metres / 1000:.2f} km -> Unity Terrain Width / Length")
    print(f"Resolution        : {summary.metres_per_pixel:.1f} m per heightmap pixel")


if __name__ == "__main__":
    main()
