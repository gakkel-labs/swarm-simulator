using UnityEngine;

namespace Gakkel.Swarm.Unity
{
    /// <summary>
    /// Real seabed of the Gakkel Ridge (Aurora hydrothermal field), built at runtime from the
    /// IBCAO 5.2 heightmap (GEBCO North Polar, 100 m) made by tools/bathymetry_to_raw.py. See docs/04-seabed-bathymetry.md.
    /// Horizontal scale is real (1 unit = 1 m). The vertical scale is adjustable, and the terrain
    /// is shifted so that its "focus" point lies under the swarm, just below the swarm volume.
    /// </summary>
    public class SeabedTerrain : MonoBehaviour
    {
        private const string HeightmapResourcePath = "Seabed/aurora_heightmap_513";
        private const string TextureResourceFolder = "Seabed/Textures/";
        private const int HeightmapResolution = 513;
        private const float SideMetres = 20000f;   // printed by bathymetry_to_raw.py
        private const float ReliefMetres = 2196f;  // deepest (4875 m) - shallowest (2679 m)
        private const float PixelError = 5f;
        private const int FootprintSamplesPerSide = 11;
        private static readonly Color SandColor = new(0.55f, 0.5f, 0.4f);

        // Aurora hydrothermal field (82.897 N, 6.255 W) is the centre of the heightmap.
        private static readonly Vector2 AuroraOffsetFromCentre = Vector2.zero;

        // Painting rule: sediment on flat ground, basalt on slopes and crests, hydrothermal around Aurora.
        // Slopes are REAL slopes (exaggeration removed), so the painting does not change with the
        // vertical exaggeration. Real slopes here: median 8°, 90 % under 18° (100 m grid),
        // so 6° → 12° gives about 40 % basalt overall.
        private const int AlphamapResolution = 512;     // ~40 m per texel, like the heightmap
        private const float BasaltSlopeStartDegrees = 6f;
        private const float BasaltSlopeEndDegrees = 12f;
        private const float RidgeBias = 0.3f;           // higher ground gets more basalt (0 = off)
        private const float EdgeNoiseDegrees = 4f;      // breaks straight sediment / basalt borders
        private const float EdgeNoiseScalePerMetre = 0.02f;
        private const float HydrothermalRadiusMetres = 150f;
        private const int SedimentLayerIndex = 0;
        private const int BasaltLayerIndex = 1;
        private const int HydrothermalLayerIndex = 2;

        private Terrain _terrain;
        private Vector3 _anchorPosition;
        private Vector2 _footprintHalfSize;
        private Vector2 _focusOffset;
        private float _verticalExaggeration = 1f;
        private Texture2D _sandTexture;
        private TerrainLayer[] _terrainLayers;
        private Material _terrainMaterial;
        private SeabedRocks _rocks;

        /// <summary>
        /// Creates the seabed. The terrain point at <paramref name="focusOffset"/> (metres from the terrain
        /// centre, X east / Z north) is placed under <paramref name="anchorPosition"/>. The highest point of
        /// the seabed inside the swarm footprint (anchor ± <paramref name="footprintHalfSize"/>) is placed
        /// at anchorPosition.y, so the relief never goes up into the swarm volume.
        /// </summary>
        public static SeabedTerrain Spawn(Vector3 anchorPosition, Vector2 footprintHalfSize,
            float verticalExaggeration, Vector2 focusOffset, bool useTextures, bool useRocks)
        {
            var heightmapAsset = Resources.Load<TextAsset>(HeightmapResourcePath);
            if (heightmapAsset == null)
            {
                Debug.LogError($"Seabed heightmap not found in Resources/{HeightmapResourcePath}.bytes");
                return null;
            }

            var terrainData = new TerrainData
            {
                heightmapResolution = HeightmapResolution,
                alphamapResolution = AlphamapResolution,
            };
            terrainData.size = new Vector3(SideMetres, ReliefMetres, SideMetres);
            terrainData.SetHeights(0, 0, DecodeHeights(heightmapAsset.bytes));
            Resources.UnloadAsset(heightmapAsset);

            var terrainObject = Terrain.CreateTerrainGameObject(terrainData);
            terrainObject.name = "Seabed";
            Destroy(terrainObject.GetComponent<TerrainCollider>()); // nothing collides with it yet

            var seabed = terrainObject.AddComponent<SeabedTerrain>();
            seabed._terrain = terrainObject.GetComponent<Terrain>();
            seabed._terrain.heightmapPixelError = PixelError;
            seabed._terrain.drawInstanced = true;
            seabed._anchorPosition = anchorPosition;
            seabed._footprintHalfSize = footprintHalfSize;
            seabed._focusOffset = focusOffset;
            seabed.SetVerticalExaggeration(verticalExaggeration);

            var textured = useTextures && seabed.TryApplyTexturedLayers();
            if (!textured) seabed.ApplySandLayer();
            seabed.SetRocksVisible(useRocks);

            Debug.Log($"Seabed (IBCAO) spawned: {SideMetres / 1000f:F1} km wide, " +
                      $"vertical exaggeration x{verticalExaggeration:F2}, focus {focusOffset} m from centre, " +
                      $"{(textured ? "textured" : "plain sand")}");
            return seabed;
        }

        /// <summary>
        /// Changes the vertical scale and keeps the seabed just under the swarm volume.
        /// The texture painting does not need to be redone: it uses real slopes.
        /// </summary>
        public void SetVerticalExaggeration(float verticalExaggeration)
        {
            if (_terrain == null) return;
            _verticalExaggeration = verticalExaggeration;
            var terrainData = _terrain.terrainData;
            terrainData.size = new Vector3(SideMetres, ReliefMetres * verticalExaggeration, SideMetres);

            // Put the focus point under the swarm first, then shift the terrain up or down.
            var position = new Vector3(
                _anchorPosition.x - SideMetres / 2f - _focusOffset.x,
                0f,
                _anchorPosition.z - SideMetres / 2f - _focusOffset.y);
            transform.position = position;
            position.y = _anchorPosition.y - HighestHeightInFootprint();
            transform.position = position;
            if (_rocks != null) _rocks.SnapToSeabed();
        }

        public bool HasVisibleRocks => _rocks != null && _rocks.gameObject.activeSelf;

        /// <summary>
        /// Shows or hides the rocks. Hidden rocks cost nothing to draw. They are made the first time
        /// they are shown, so a scene started without rocks does not pay for them.
        /// </summary>
        public void SetRocksVisible(bool visible)
        {
            if (visible && _rocks == null)
                _rocks = SeabedRocks.Spawn(this, _anchorPosition, _footprintHalfSize);
            if (_rocks != null) _rocks.gameObject.SetActive(visible);
        }

        /// <summary>World height of the seabed at a world X/Z.</summary>
        public float GroundHeightAt(Vector3 worldPosition)
            => transform.position.y + _terrain.SampleHeight(worldPosition);

        /// <summary>Normal of the seabed as shown (with the vertical exaggeration) at a world X/Z.</summary>
        public Vector3 GroundNormalAt(Vector3 worldPosition)
        {
            var (u, v) = ToTerrainFractions(worldPosition);
            return _terrain.terrainData.GetInterpolatedNormal(u, v);
        }

        /// <summary>How much basalt (0..1) the painting rule puts at a world X/Z.</summary>
        public float BasaltWeightAt(Vector3 worldPosition)
        {
            var (u, v) = ToTerrainFractions(worldPosition);
            return BasaltWeight(u, v);
        }

        /// <summary>How much hydrothermal deposit (0..1) the painting rule puts at a world X/Z.</summary>
        public float HydrothermalWeightAt(Vector3 worldPosition)
        {
            var (u, v) = ToTerrainFractions(worldPosition);
            return HydrothermalWeight(u, v);
        }

        // u (east) and v (north) go from 0 to 1 across the terrain.
        private (float u, float v) ToTerrainFractions(Vector3 worldPosition)
        {
            var localPosition = worldPosition - transform.position;
            return (Mathf.Clamp01(localPosition.x / SideMetres), Mathf.Clamp01(localPosition.z / SideMetres));
        }

        // Terrain.SampleHeight gives the height above the terrain's own position, at a world X/Z.
        private float HighestHeightInFootprint()
        {
            var highestHeight = float.MinValue;
            for (var xIndex = 0; xIndex < FootprintSamplesPerSide; xIndex++)
            {
                for (var zIndex = 0; zIndex < FootprintSamplesPerSide; zIndex++)
                {
                    var xFraction = xIndex / (FootprintSamplesPerSide - 1f) * 2f - 1f;
                    var zFraction = zIndex / (FootprintSamplesPerSide - 1f) * 2f - 1f;
                    var samplePosition = _anchorPosition + new Vector3(
                        xFraction * _footprintHalfSize.x, 0f, zFraction * _footprintHalfSize.y);
                    highestHeight = Mathf.Max(highestHeight, _terrain.SampleHeight(samplePosition));
                }
            }
            return highestHeight;
        }

        // RAW file: 16-bit unsigned, little-endian, south row first (row = Unity Z, column = Unity X).
        private static float[,] DecodeHeights(byte[] rawBytes)
        {
            const int expectedByteCount = HeightmapResolution * HeightmapResolution * 2;
            if (rawBytes.Length != expectedByteCount)
                throw new System.ArgumentException($"Heightmap has {rawBytes.Length} bytes, expected {expectedByteCount}.");

            var heights = new float[HeightmapResolution, HeightmapResolution];
            for (var row = 0; row < HeightmapResolution; row++)
            {
                for (var column = 0; column < HeightmapResolution; column++)
                {
                    var byteIndex = 2 * (row * HeightmapResolution + column);
                    var rawHeight = rawBytes[byteIndex] | (rawBytes[byteIndex + 1] << 8);
                    heights[row, column] = rawHeight / (float)ushort.MaxValue;
                }
            }
            return heights;
        }

        // ---------- Textured seabed ----------

        private bool TryApplyTexturedLayers()
        {
            // Large tiles: seen from tens of metres, small tiles show a repeating pattern.
            var sediment = LoadTerrainLayer("sediment", 12f);
            var basalt = LoadTerrainLayer("basalt", 15f);
            var hydrothermal = LoadTerrainLayer("hydrothermal", 10f);
            if (sediment == null || basalt == null || hydrothermal == null)
            {
                Debug.LogWarning($"Seabed textures missing in Resources/{TextureResourceFolder}: plain sand instead.");
                return false;
            }

            // The URP terrain shader only reads normal and mask maps when these keywords are on.
            // (The Terrain inspector turns them on for terrains made in the Editor; ours is made by code.)
            var terrainShader = Shader.Find("Universal Render Pipeline/Terrain/Lit");
            if (terrainShader != null)
            {
                _terrainMaterial = new Material(terrainShader);
                _terrainMaterial.EnableKeyword("_NORMALMAP");
                _terrainMaterial.EnableKeyword("_MASKMAP");
                _terrain.materialTemplate = _terrainMaterial;
            }

            _terrainLayers = new[] { sediment, basalt, hydrothermal };
            _terrain.terrainData.terrainLayers = _terrainLayers;
            PaintLayers();
            return true;
        }

        private static TerrainLayer LoadTerrainLayer(string materialName, float tileSizeMetres)
        {
            var albedo = Resources.Load<Texture2D>($"{TextureResourceFolder}{materialName}_albedo");
            if (albedo == null) return null;
            return new TerrainLayer
            {
                diffuseTexture = albedo,
                normalMapTexture = Resources.Load<Texture2D>($"{TextureResourceFolder}{materialName}_normal"),
                maskMapTexture = Resources.Load<Texture2D>($"{TextureResourceFolder}{materialName}_mask"),
                tileSize = new Vector2(tileSizeMetres, tileSizeMetres),
            };
        }

        // Same rule as the Editor tool TerrainAutoPaint, but run at startup on the runtime terrain.
        private void PaintLayers()
        {
            var terrainData = _terrain.terrainData;
            var resolution = terrainData.alphamapResolution;
            var weights = new float[resolution, resolution, _terrainLayers.Length];

            for (var row = 0; row < resolution; row++)
            {
                var v = row / (resolution - 1f);
                for (var column = 0; column < resolution; column++)
                {
                    var u = column / (resolution - 1f);
                    var basalt = BasaltWeight(u, v);
                    var hydrothermal = HydrothermalWeight(u, v);

                    weights[row, column, SedimentLayerIndex] = (1f - basalt) * (1f - hydrothermal);
                    weights[row, column, BasaltLayerIndex] = basalt * (1f - hydrothermal);
                    weights[row, column, HydrothermalLayerIndex] = hydrothermal;
                }
            }
            terrainData.SetAlphamaps(0, 0, weights);
        }

        // Basalt on slopes and crests. u, v go from 0 to 1 across the terrain.
        private float BasaltWeight(float u, float v)
        {
            var terrainData = _terrain.terrainData;
            var localX = u * SideMetres;
            var localZ = v * SideMetres;

            // Heights are normalised 0..1 over the whole area, so this is also "how high on the ridge".
            // GetSteepness measures the exaggerated slope: tan(real) = tan(shown) / exaggeration.
            var shownSlopeRadians = terrainData.GetSteepness(u, v) * Mathf.Deg2Rad;
            var slopeDegrees = Mathf.Atan(Mathf.Tan(shownSlopeRadians) / _verticalExaggeration) * Mathf.Rad2Deg;
            var height01 = terrainData.GetInterpolatedHeight(u, v) / terrainData.size.y;
            var edgeNoise = (Mathf.PerlinNoise(localX * EdgeNoiseScalePerMetre, localZ * EdgeNoiseScalePerMetre) - 0.5f)
                            * 2f * EdgeNoiseDegrees;
            var effectiveSlope = slopeDegrees + edgeNoise
                + RidgeBias * (height01 - 0.5f) * (BasaltSlopeEndDegrees - BasaltSlopeStartDegrees) * 2f;
            return SmoothStep(BasaltSlopeStartDegrees, BasaltSlopeEndDegrees, effectiveSlope);
        }

        // Hydrothermal deposit in a ragged disc around the Aurora vent field.
        private static float HydrothermalWeight(float u, float v)
        {
            var localX = u * SideMetres;
            var localZ = v * SideMetres;
            var auroraLocal = new Vector2(SideMetres / 2f, SideMetres / 2f) + AuroraOffsetFromCentre;
            var distanceToAurora = Vector2.Distance(new Vector2(localX, localZ), auroraLocal);
            var ventRadius = HydrothermalRadiusMetres
                * (1f + 0.35f * (Mathf.PerlinNoise(localX * 0.05f + 7.3f, localZ * 0.05f) - 0.5f) * 2f);
            return 1f - SmoothStep(ventRadius * 0.4f, ventRadius, distanceToAurora);
        }

        private static float SmoothStep(float edgeStart, float edgeEnd, float value)
        {
            var t = Mathf.Clamp01((value - edgeStart) / Mathf.Max(1e-5f, edgeEnd - edgeStart));
            return t * t * (3f - 2f * t);
        }

        // ---------- Plain seabed ----------

        private void ApplySandLayer()
        {
            _sandTexture = new Texture2D(1, 1);
            _sandTexture.SetPixel(0, 0, SandColor);
            _sandTexture.Apply();
            _terrainLayers = new[] { new TerrainLayer { diffuseTexture = _sandTexture, tileSize = new Vector2(10f, 10f) } };
            _terrain.terrainData.terrainLayers = _terrainLayers;
        }

        private void OnDestroy()
        {
            if (_terrainLayers != null)
                foreach (var terrainLayer in _terrainLayers) Destroy(terrainLayer);
            Destroy(_terrainMaterial);
            Destroy(_sandTexture);
            if (_terrain != null) Destroy(_terrain.terrainData);
        }
    }
}
