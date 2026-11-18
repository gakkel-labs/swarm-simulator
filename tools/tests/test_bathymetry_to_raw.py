"""Tests for tools/bathymetry_to_raw.py (pytest, Arrange / Act / Assert)."""

import sys
from pathlib import Path

import numpy as np
import pytest
import rasterio
from rasterio.transform import from_bounds
from rasterio.warp import transform as transform_points

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from bathymetry_to_raw import (  # noqa: E402
    AURORA,
    GeoPoint,
    convert,
    normalize_to_uint16,
    sample_square,
    write_raw,
)

GEOGRAPHIC_CRS = rasterio.crs.CRS.from_epsg(4326)
IBCAO_CRS = rasterio.crs.CRS.from_epsg(3996)


def geographic_grid(row_count, column_count, values_from_lat_lon):
    """Small lat/lon grid around Aurora (like the GEBCO file). Rows go north to south."""
    west, south, east, north = -7.0, 82.8, -5.5, 83.0
    grid_transform = from_bounds(west, south, east, north, column_count, row_count)
    rows, columns = np.mgrid[0:row_count, 0:column_count]
    longitudes, latitudes = grid_transform @ (columns + 0.5, rows + 0.5)
    return values_from_lat_lon(latitudes, longitudes), grid_transform


def polar_grid(values_from_lat_lon, cell_metres=100.0, half_side_metres=13_000.0):
    """Polar stereographic grid around Aurora (like the IBCAO file)."""
    (aurora_x,), (aurora_y,) = transform_points(GEOGRAPHIC_CRS, IBCAO_CRS, [AURORA.longitude], [AURORA.latitude])
    cell_count = int(2 * half_side_metres / cell_metres)
    grid_transform = from_bounds(
        aurora_x - half_side_metres, aurora_y - half_side_metres,
        aurora_x + half_side_metres, aurora_y + half_side_metres, cell_count, cell_count,
    )
    rows, columns = np.mgrid[0:cell_count, 0:cell_count]
    xs, ys = grid_transform @ (columns + 0.5, rows + 0.5)
    longitudes, latitudes = transform_points(IBCAO_CRS, GEOGRAPHIC_CRS, xs.ravel(), ys.ravel())
    values = values_from_lat_lon(np.reshape(latitudes, xs.shape), np.reshape(longitudes, xs.shape))
    return values, grid_transform


def test_normalize_maps_deepest_to_zero_and_shallowest_to_max():
    # Arrange
    elevations = np.array([[-4000.0, -3500.0], [-3000.0, -3750.0]])

    # Act
    heights, min_elevation, max_elevation = normalize_to_uint16(elevations)

    # Assert
    assert heights.dtype == np.uint16
    assert heights[0, 0] == 0
    assert heights[1, 0] == 65535
    assert heights[0, 1] > heights[1, 1]
    assert (min_elevation, max_elevation) == (-4000.0, -3000.0)


def test_normalize_rejects_flat_input():
    # Arrange
    flat_elevations = np.full((3, 3), -4000.0)

    # Act / Assert
    with pytest.raises(ValueError):
        normalize_to_uint16(flat_elevations)


def test_write_raw_writes_16_bit_little_endian(tmp_path):
    # Arrange
    heights = np.zeros((513, 513), dtype=np.uint16)
    heights[0, 0] = 0x0102
    output_path = tmp_path / "heightmap.raw"

    # Act
    write_raw(heights, output_path)

    # Assert
    raw_bytes = output_path.read_bytes()
    assert len(raw_bytes) == 513 * 513 * 2
    assert raw_bytes[:2] == b"\x02\x01"


def test_sample_square_puts_south_in_first_row_and_west_in_first_column():
    # Arrange: deeper to the south-west.
    elevations, grid_transform = geographic_grid(
        48, 360, lambda latitudes, longitudes: -4000.0 + 1000.0 * (latitudes - 82.8) + 100.0 * (longitudes + 7.0)
    )

    # Act
    square = sample_square(elevations, grid_transform, GEOGRAPHIC_CRS, AURORA, side_metres=10_000, size=33)

    # Assert
    assert square.shape == (33, 33)
    assert square[0].mean() < square[-1].mean()        # row 0 = south
    assert square[:, 0].mean() < square[:, -1].mean()  # column 0 = west


def test_sample_square_follows_true_north_on_a_polar_grid():
    # Arrange: depth depends only on latitude. In polar stereographic the grid is rotated
    # (about 6 degrees here), so a naive crop would mix latitudes along one row.
    elevations, grid_transform = polar_grid(lambda latitudes, longitudes: -4000.0 + 50_000.0 * (latitudes - AURORA.latitude))

    # Act
    square = sample_square(elevations, grid_transform, IBCAO_CRS, AURORA, side_metres=20_000, size=65)

    # Assert: each row (same northing) is almost flat, while the rows differ a lot.
    spread_along_rows = np.abs(square - square.mean(axis=1, keepdims=True)).max()
    north_south_change = square[-1].mean() - square[0].mean()
    assert north_south_change > 500
    assert spread_along_rows < 0.02 * north_south_change


def test_sample_square_rejects_a_square_outside_the_data():
    # Arrange
    elevations, grid_transform = polar_grid(lambda latitudes, longitudes: -4000.0 + 0.0 * latitudes)

    # Act / Assert
    with pytest.raises(ValueError):
        sample_square(elevations, grid_transform, IBCAO_CRS, AURORA, side_metres=40_000, size=33)


def test_sample_square_is_smooth_between_coarse_rows():
    # Arrange: few coarse rows following a smooth curve (like the 48 GEBCO rows).
    # Bilinear interpolation would draw straight segments between rows and leave "kinks"
    # that show up as regular stripes under the light in Unity.
    elevations, grid_transform = geographic_grid(
        8, 8, lambda latitudes, longitudes: -3500.0 - 500.0 * ((latitudes - 82.9) / 0.1) ** 2
    )

    # Act
    square = sample_square(elevations, grid_transform, GEOGRAPHIC_CRS, GeoPoint(82.9, -6.25), side_metres=15_000, size=129)

    # Assert: the second difference (change of slope) never jumps.
    second_difference = np.abs(np.diff(square[:, 64], n=2))
    assert second_difference.max() < 3 * np.median(second_difference)


def write_geotiff(path, elevations, grid_transform, crs):
    with rasterio.open(
        path, "w", driver="GTiff", height=elevations.shape[0], width=elevations.shape[1], count=1,
        dtype="float32", crs=crs, transform=grid_transform,
    ) as dataset:
        dataset.write(elevations.astype(np.float32), 1)


def test_convert_geographic_geotiff_end_to_end(tmp_path):
    # Arrange: depth grows from north to south, like the GEBCO file layout.
    elevations, grid_transform = geographic_grid(
        48, 360, lambda latitudes, longitudes: -4000.0 + 5000.0 * (latitudes - 82.8)
    )
    input_path = tmp_path / "gebco.tif"
    write_geotiff(input_path, elevations, grid_transform, GEOGRAPHIC_CRS)
    output_path = tmp_path / "heightmap.raw"

    # Act
    summary = convert(input_path, output_path, size=513, side_metres=20_000)

    # Assert
    heights = np.fromfile(output_path, dtype="<u2").reshape(513, 513)
    assert heights[0].max() < heights[-1].min()
    assert summary.side_metres == 20_000
    assert summary.metres_per_pixel == pytest.approx(20_000 / 512)
    assert summary.centre_depth_metres == pytest.approx(4000.0 - 5000.0 * (AURORA.latitude - 82.8), abs=2)


def test_convert_polar_geotiff_end_to_end(tmp_path):
    # Arrange: IBCAO-like file (EPSG:3996, 100 m cells).
    elevations, grid_transform = polar_grid(lambda latitudes, longitudes: -4000.0 + 5000.0 * (latitudes - AURORA.latitude))
    input_path = tmp_path / "ibcao.tif"
    write_geotiff(input_path, elevations, grid_transform, IBCAO_CRS)
    output_path = tmp_path / "heightmap.raw"

    # Act
    summary = convert(input_path, output_path, size=513, side_metres=20_000)

    # Assert
    assert output_path.stat().st_size == 513 * 513 * 2
    assert summary.centre_depth_metres == pytest.approx(4000.0, abs=2)
    assert summary.min_depth_metres < 4000.0 < summary.max_depth_metres
