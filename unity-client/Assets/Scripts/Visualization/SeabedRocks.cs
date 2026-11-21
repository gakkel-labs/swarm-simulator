using System.Collections.Generic;
using UnityEngine;

namespace Gakkel.Swarm.Unity
{
    /// <summary>
    /// Basalt boulders and pebbles on the seabed around the swarm. The bathymetry grid (about 40 m per
    /// sample) only shows hills, so these rocks give the scale seen from the drones.
    /// Many rocks where the seabed is basalt (slopes, crests), few on sediment, none on the hydrothermal
    /// field. The rock shapes are made by code (no 3D asset). Same seed every run: the scene always looks
    /// the same, which helps for screenshots. Display only, nothing collides with the rocks.
    /// </summary>
    public class SeabedRocks : MonoBehaviour
    {
        private const string TextureResourceFolder = "Seabed/Textures/";
        private const int RandomSeed = 75;
        private const int RockShapeCount = 8;
        private const int IcosphereSubdivisions = 2;
        private const int CandidateCount = 3000;
        private const float ScatterRadiusMetres = 250f;
        private const float MinRockSizeMetres = 0.3f;
        private const float MaxRockSizeMetres = 4f;
        private const float SizeDistributionPower = 3f;         // higher = more small rocks
        private const float MaxRockSizeInFootprintMetres = 1f;  // big rocks would poke into the swarm volume
        private const float SedimentRockChance = 0.06f;
        private const float BasaltRockChance = 0.9f;
        private const float ClusterNoiseScalePerMetre = 0.03f;  // rocks gather in patches
        private const float SunkFraction = 0.3f;                // part of the rock height under the sediment
        private const float AlignToSlope = 0.7f;                // 0 = upright, 1 = follows the slope
        private const float TextureRepeatsPerMetre = 0.5f;
        private const float RockSmoothness = 0.15f;
        private static readonly Color RockTint = new(0.8f, 0.8f, 0.82f);
        private static readonly Color PlainRockColor = new(0.18f, 0.18f, 0.2f);

        private readonly List<PlacedRock> _placedRocks = new();
        private readonly List<Mesh> _rockMeshes = new();
        private SeabedTerrain _seabed;
        private Material _rockMaterial;

        private struct PlacedRock
        {
            public Transform RockTransform;
            public float SunkDepthMetres;
        }

        /// <summary>
        /// Scatters rocks in a disc around <paramref name="centre"/>. Inside the swarm footprint
        /// (centre ± <paramref name="footprintHalfSize"/>) only small rocks are placed.
        /// </summary>
        public static SeabedRocks Spawn(SeabedTerrain seabed, Vector3 centre, Vector2 footprintHalfSize)
        {
            var rocksObject = new GameObject("Rocks");
            rocksObject.transform.SetParent(seabed.transform, false);
            var rocks = rocksObject.AddComponent<SeabedRocks>();
            rocks._seabed = seabed;
            rocks._rockMaterial = CreateRockMaterial();

            var random = new System.Random(RandomSeed);
            for (var shapeIndex = 0; shapeIndex < RockShapeCount; shapeIndex++)
                rocks._rockMeshes.Add(RockMeshBuilder.Build(random));

            rocks.Scatter(random, centre, footprintHalfSize);
            Debug.Log($"Seabed rocks: {rocks._placedRocks.Count} placed within {ScatterRadiusMetres:F0} m of the swarm");
            return rocks;
        }

        /// <summary>Puts every rock back on the ground, after the vertical exaggeration has changed.</summary>
        public void SnapToSeabed()
        {
            foreach (var placedRock in _placedRocks)
            {
                var position = placedRock.RockTransform.position;
                position.y = _seabed.GroundHeightAt(position) - placedRock.SunkDepthMetres;
                placedRock.RockTransform.position = position;
            }
        }

        private void Scatter(System.Random random, Vector3 centre, Vector2 footprintHalfSize)
        {
            for (var candidateIndex = 0; candidateIndex < CandidateCount; candidateIndex++)
            {
                // Uniform point in the disc (square root, otherwise points crowd the centre).
                var angle = (float)(random.NextDouble() * 2.0 * Mathf.PI);
                var distance = ScatterRadiusMetres * Mathf.Sqrt((float)random.NextDouble());
                var position = centre + new Vector3(Mathf.Cos(angle) * distance, 0f, Mathf.Sin(angle) * distance);
                var acceptDraw = (float)random.NextDouble();
                var sizeDraw = (float)random.NextDouble();

                if (acceptDraw > RockChanceAt(position)) continue;

                var sizeMetres = MinRockSizeMetres
                    * Mathf.Pow(MaxRockSizeMetres / MinRockSizeMetres, Mathf.Pow(sizeDraw, SizeDistributionPower));
                var isInFootprint = Mathf.Abs(position.x - centre.x) <= footprintHalfSize.x
                                    && Mathf.Abs(position.z - centre.z) <= footprintHalfSize.y;
                if (isInFootprint) sizeMetres = Mathf.Min(sizeMetres, MaxRockSizeInFootprintMetres);

                PlaceRock(random, position, sizeMetres);
            }
        }

        private float RockChanceAt(Vector3 position)
        {
            var basaltWeight = _seabed.BasaltWeightAt(position);
            var hydrothermalWeight = _seabed.HydrothermalWeightAt(position);
            var cluster = Mathf.PerlinNoise(position.x * ClusterNoiseScalePerMetre + 100f,
                                            position.z * ClusterNoiseScalePerMetre + 100f);
            var clusterFactor = Mathf.Clamp01(cluster * 2f - 0.4f);
            return Mathf.Lerp(SedimentRockChance, BasaltRockChance, basaltWeight)
                   * (1f - hydrothermalWeight) * clusterFactor;
        }

        private void PlaceRock(System.Random random, Vector3 position, float sizeMetres)
        {
            var rockObject = new GameObject("Rock");
            rockObject.transform.SetParent(transform, false);
            rockObject.AddComponent<MeshFilter>().sharedMesh = _rockMeshes[random.Next(_rockMeshes.Count)];
            rockObject.AddComponent<MeshRenderer>().sharedMaterial = _rockMaterial;

            // Boulders are wider than tall, like rocks that have settled.
            var heightFactor = Mathf.Lerp(0.45f, 0.8f, (float)random.NextDouble());
            var depthFactor = Mathf.Lerp(0.7f, 1.1f, (float)random.NextDouble());
            rockObject.transform.localScale = sizeMetres * new Vector3(1f, heightFactor, depthFactor);

            var groundUp = Vector3.Slerp(Vector3.up, _seabed.GroundNormalAt(position), AlignToSlope);
            var yawDegrees = (float)random.NextDouble() * 360f;
            rockObject.transform.rotation = Quaternion.FromToRotation(Vector3.up, groundUp)
                                            * Quaternion.Euler(0f, yawDegrees, 0f);

            // The mesh has a radius of about 0.5, so its height is about sizeMetres * heightFactor.
            var sunkDepth = SunkFraction * sizeMetres * heightFactor;
            position.y = _seabed.GroundHeightAt(position) - sunkDepth;
            rockObject.transform.position = position;
            _placedRocks.Add(new PlacedRock { RockTransform = rockObject.transform, SunkDepthMetres = sunkDepth });
        }

        private static Material CreateRockMaterial()
        {
            var material = new Material(Shader.Find("Universal Render Pipeline/Lit")) { enableInstancing = true };
            var albedo = Resources.Load<Texture2D>($"{TextureResourceFolder}basalt_albedo");
            var normal = Resources.Load<Texture2D>($"{TextureResourceFolder}basalt_normal");
            if (albedo != null)
            {
                material.SetTexture("_BaseMap", albedo);
                material.SetColor("_BaseColor", RockTint);
            }
            else
            {
                material.SetColor("_BaseColor", PlainRockColor);
            }
            if (normal != null)
            {
                material.SetTexture("_BumpMap", normal);
                material.EnableKeyword("_NORMALMAP");
            }
            material.SetFloat("_Smoothness", RockSmoothness);
            material.SetFloat("_Metallic", 0f);
            return material;
        }

        private void OnDestroy()
        {
            foreach (var rockMesh in _rockMeshes) Destroy(rockMesh);
            Destroy(_rockMaterial);
        }

        /// <summary>
        /// Builds one low-poly rock: a sphere made of triangles (icosphere), bumped with noise, then cut
        /// by a few planes to look like broken basalt. Flat shading (each triangle has its own vertices)
        /// gives sharp edges.
        /// </summary>
        private static class RockMeshBuilder
        {
            private const float BaseRadius = 0.5f;
            private const float LargeBumpAmount = 0.35f;
            private const float SmallBumpAmount = 0.08f;
            private const int CutPlaneCount = 3;

            public static Mesh Build(System.Random random)
            {
                var (sphereVertices, sphereTriangles) = BuildIcosphere(IcosphereSubdivisions);
                var noiseOffset = new Vector3((float)random.NextDouble(), (float)random.NextDouble(),
                                              (float)random.NextDouble()) * 100f;

                for (var vertexIndex = 0; vertexIndex < sphereVertices.Count; vertexIndex++)
                {
                    var direction = sphereVertices[vertexIndex];
                    var largeBump = (Noise3D(direction * 1.5f + noiseOffset) - 0.5f) * 2f * LargeBumpAmount;
                    var smallBump = (Noise3D(direction * 5f + noiseOffset) - 0.5f) * 2f * SmallBumpAmount;
                    sphereVertices[vertexIndex] = direction * BaseRadius * (1f + largeBump + smallBump);
                }

                for (var cutIndex = 0; cutIndex < CutPlaneCount; cutIndex++)
                {
                    var planeNormal = RandomUnitVector(random);
                    var planeDistance = BaseRadius * Mathf.Lerp(0.55f, 0.8f, (float)random.NextDouble());
                    for (var vertexIndex = 0; vertexIndex < sphereVertices.Count; vertexIndex++)
                    {
                        var vertex = sphereVertices[vertexIndex];
                        var beyondPlane = Vector3.Dot(vertex, planeNormal) - planeDistance;
                        if (beyondPlane > 0f) sphereVertices[vertexIndex] = vertex - beyondPlane * planeNormal;
                    }
                }

                return BuildFlatShadedMesh(sphereVertices, sphereTriangles);
            }

            private static Mesh BuildFlatShadedMesh(List<Vector3> sphereVertices, List<int> sphereTriangles)
            {
                var vertices = new Vector3[sphereTriangles.Count];
                var uvs = new Vector2[sphereTriangles.Count];
                var triangles = new int[sphereTriangles.Count];
                for (var cornerIndex = 0; cornerIndex < sphereTriangles.Count; cornerIndex += 3)
                {
                    var cornerA = sphereVertices[sphereTriangles[cornerIndex]];
                    var cornerB = sphereVertices[sphereTriangles[cornerIndex + 1]];
                    var cornerC = sphereVertices[sphereTriangles[cornerIndex + 2]];
                    var faceNormal = Vector3.Cross(cornerB - cornerA, cornerC - cornerA);

                    vertices[cornerIndex] = cornerA;
                    vertices[cornerIndex + 1] = cornerB;
                    vertices[cornerIndex + 2] = cornerC;
                    uvs[cornerIndex] = ProjectOnDominantAxis(cornerA, faceNormal);
                    uvs[cornerIndex + 1] = ProjectOnDominantAxis(cornerB, faceNormal);
                    uvs[cornerIndex + 2] = ProjectOnDominantAxis(cornerC, faceNormal);
                    triangles[cornerIndex] = cornerIndex;
                    triangles[cornerIndex + 1] = cornerIndex + 1;
                    triangles[cornerIndex + 2] = cornerIndex + 2;
                }

                var mesh = new Mesh { name = "Rock", vertices = vertices, uv = uvs, triangles = triangles };
                mesh.RecalculateNormals();
                mesh.RecalculateTangents(); // the normal map needs tangents
                mesh.RecalculateBounds();
                return mesh;
            }

            // Planar texture projection on the axis the face looks at (a simple "triplanar" mapping).
            private static Vector2 ProjectOnDominantAxis(Vector3 vertex, Vector3 faceNormal)
            {
                var absoluteNormal = new Vector3(Mathf.Abs(faceNormal.x), Mathf.Abs(faceNormal.y), Mathf.Abs(faceNormal.z));
                Vector2 projected;
                if (absoluteNormal.x >= absoluteNormal.y && absoluteNormal.x >= absoluteNormal.z)
                    projected = new Vector2(vertex.z, vertex.y);
                else if (absoluteNormal.y >= absoluteNormal.z)
                    projected = new Vector2(vertex.x, vertex.z);
                else
                    projected = new Vector2(vertex.x, vertex.y);
                return projected * (TextureRepeatsPerMetre * MaxRockSizeMetres);
            }

            // Unity has no 3D Perlin noise: the average of three 2D noises is enough for rock bumps.
            private static float Noise3D(Vector3 point)
            {
                return (Mathf.PerlinNoise(point.x, point.y)
                        + Mathf.PerlinNoise(point.y, point.z)
                        + Mathf.PerlinNoise(point.z, point.x)) / 3f;
            }

            private static Vector3 RandomUnitVector(System.Random random)
            {
                var height = (float)(random.NextDouble() * 2.0 - 1.0);
                var angle = (float)(random.NextDouble() * 2.0 * Mathf.PI);
                var horizontalRadius = Mathf.Sqrt(1f - height * height);
                return new Vector3(horizontalRadius * Mathf.Cos(angle), height, horizontalRadius * Mathf.Sin(angle));
            }

            // Icosahedron (12 vertices, 20 faces), each face split in 4 at every subdivision.
            // Vertices are on the unit sphere.
            private static (List<Vector3> vertices, List<int> triangles) BuildIcosphere(int subdivisions)
            {
                var goldenRatio = (1f + Mathf.Sqrt(5f)) / 2f;
                var vertices = new List<Vector3>
                {
                    new(-1, goldenRatio, 0), new(1, goldenRatio, 0), new(-1, -goldenRatio, 0), new(1, -goldenRatio, 0),
                    new(0, -1, goldenRatio), new(0, 1, goldenRatio), new(0, -1, -goldenRatio), new(0, 1, -goldenRatio),
                    new(goldenRatio, 0, -1), new(goldenRatio, 0, 1), new(-goldenRatio, 0, -1), new(-goldenRatio, 0, 1),
                };
                for (var vertexIndex = 0; vertexIndex < vertices.Count; vertexIndex++)
                    vertices[vertexIndex] = vertices[vertexIndex].normalized;

                var triangles = new List<int>
                {
                    0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11,
                    1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1, 8,
                    3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9,
                    4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1,
                };

                for (var level = 0; level < subdivisions; level++)
                {
                    var midpointCache = new Dictionary<long, int>();
                    var subdividedTriangles = new List<int>(triangles.Count * 4);
                    for (var cornerIndex = 0; cornerIndex < triangles.Count; cornerIndex += 3)
                    {
                        var cornerA = triangles[cornerIndex];
                        var cornerB = triangles[cornerIndex + 1];
                        var cornerC = triangles[cornerIndex + 2];
                        var midpointAb = Midpoint(vertices, midpointCache, cornerA, cornerB);
                        var midpointBc = Midpoint(vertices, midpointCache, cornerB, cornerC);
                        var midpointCa = Midpoint(vertices, midpointCache, cornerC, cornerA);
                        subdividedTriangles.AddRange(new[]
                        {
                            cornerA, midpointAb, midpointCa,
                            cornerB, midpointBc, midpointAb,
                            cornerC, midpointCa, midpointBc,
                            midpointAb, midpointBc, midpointCa,
                        });
                    }
                    triangles = subdividedTriangles;
                }
                return (vertices, triangles);
            }

            private static int Midpoint(List<Vector3> vertices, Dictionary<long, int> midpointCache,
                                        int firstIndex, int secondIndex)
            {
                var smallerIndex = Mathf.Min(firstIndex, secondIndex);
                var largerIndex = Mathf.Max(firstIndex, secondIndex);
                var edgeKey = ((long)smallerIndex << 32) + largerIndex;
                if (midpointCache.TryGetValue(edgeKey, out var existingIndex)) return existingIndex;

                vertices.Add(((vertices[firstIndex] + vertices[secondIndex]) / 2f).normalized);
                midpointCache[edgeKey] = vertices.Count - 1;
                return vertices.Count - 1;
            }
        }
    }
}
