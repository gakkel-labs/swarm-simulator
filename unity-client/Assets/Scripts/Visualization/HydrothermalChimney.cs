using System.Collections.Generic;
using UnityEngine;

namespace Gakkel.Swarm.Unity
{
    /// <summary>
    /// Shows one simulation obstacle as a hydrothermal chimney (a "black smoker"), like the ones of the
    /// Aurora vent field. The chimney stands on the seabed and rises up to the obstacle.
    /// Detailed view: a lumpy mineral spire with smaller spires around it, and black smoke at the top.
    /// Simple view: a plain dark column. Display only: the simulation still sees a sphere
    /// (the obstacle position and radius), so a drone passing under that sphere can cross the column.
    /// </summary>
    public class HydrothermalChimney : MonoBehaviour
    {
        private const string TextureResourceFolder = "Seabed/Textures/";
        private const int RingSections = 16;
        private const float RingSpacingMetres = 1.5f;
        private const float TopRadiusFraction = 0.3f;   // top radius / base radius
        private const float CraterTopRadiusFraction = 0.6f;    // crater opening / top radius
        private const float CraterBottomRadiusFraction = 0.4f;
        private const float CraterMaxDepthMetres = 2f;
        private const float BumpAmount = 0.25f;
        private const float TopAboveObstacleCentreFraction = 0.5f; // in obstacle radii
        private const int SideSpireCount = 3;
        private const float SimpleColumnRadiusFraction = 0.6f;
        private const float TextureMetresPerRepeat = 6f;
        private static readonly Color SimpleColumnColor = new(0.15f, 0.13f, 0.12f);
        private static readonly Color SmokeColor = new(0.05f, 0.05f, 0.06f, 0.55f);

        private GameObject _detailed;
        private GameObject _simple;
        private readonly List<Object> _ownedAssets = new();

        /// <summary>
        /// Creates a chimney under the obstacle at <paramref name="obstaclePosition"/> (world),
        /// from the seabed (<paramref name="groundHeight"/>) up to a bit above the obstacle centre.
        /// </summary>
        public static HydrothermalChimney Spawn(Vector3 obstaclePosition, float obstacleRadius, float groundHeight,
            int seed)
        {
            var chimneyObject = new GameObject("HydrothermalChimney");
            chimneyObject.transform.position = new Vector3(obstaclePosition.x, groundHeight, obstaclePosition.z);
            var chimney = chimneyObject.AddComponent<HydrothermalChimney>();

            var heightMetres = Mathf.Max(3f, obstaclePosition.y + TopAboveObstacleCentreFraction * obstacleRadius - groundHeight);
            var baseRadius = Mathf.Max(1f, obstacleRadius);
            chimney.BuildDetailed(heightMetres, baseRadius, new System.Random(seed));
            chimney.BuildSimple(heightMetres, baseRadius);

            chimney.ApplyView(DetailedView.IsOn);
            DetailedView.Changed += chimney.ApplyView;
            return chimney;
        }

        private void ApplyView(bool isDetailed)
        {
            _detailed.SetActive(isDetailed);
            _simple.SetActive(!isDetailed);
        }

        // ---------- Detailed ----------

        private void BuildDetailed(float heightMetres, float baseRadius, System.Random random)
        {
            _detailed = new GameObject("Detailed");
            _detailed.transform.SetParent(transform, false);
            var material = CreateMineralMaterial();

            AddSpire(_detailed.transform, Vector3.zero, heightMetres, baseRadius, material, random);
            for (var spireIndex = 0; spireIndex < SideSpireCount; spireIndex++)
            {
                var angle = (float)(random.NextDouble() * 2.0 * Mathf.PI);
                var distance = baseRadius * Mathf.Lerp(0.9f, 1.6f, (float)random.NextDouble());
                var offset = new Vector3(Mathf.Cos(angle) * distance, -0.3f, Mathf.Sin(angle) * distance);
                var spireHeight = heightMetres * Mathf.Lerp(0.15f, 0.4f, (float)random.NextDouble());
                AddSpire(_detailed.transform, offset, spireHeight, baseRadius * 0.4f, material, random);
            }

            AddSmoke(_detailed.transform, new Vector3(0f, heightMetres, 0f), baseRadius * TopRadiusFraction);
        }

        private void AddSpire(Transform parent, Vector3 localPosition, float heightMetres, float baseRadius,
            Material material, System.Random random)
        {
            var spireObject = new GameObject("Spire");
            spireObject.transform.SetParent(parent, false);
            spireObject.transform.localPosition = localPosition;
            var mesh = BuildSpireMesh(heightMetres, baseRadius, random);
            _ownedAssets.Add(mesh);
            spireObject.AddComponent<MeshFilter>().sharedMesh = mesh;
            spireObject.AddComponent<MeshRenderer>().sharedMaterial = material;
        }

        // Stacked rings, narrower towards the top, each one bumped with noise (chimneys grow in lumps).
        private static Mesh BuildSpireMesh(float heightMetres, float baseRadius, System.Random random)
        {
            var ringCount = Mathf.Max(3, Mathf.CeilToInt(heightMetres / RingSpacingMetres) + 1);
            var noiseOffset = (float)random.NextDouble() * 100f;
            var vertices = new List<Vector3>();
            var uvs = new List<Vector2>();
            var triangles = new List<int>();

            for (var ringIndex = 0; ringIndex < ringCount; ringIndex++)
            {
                var heightFraction = ringIndex / (ringCount - 1f);
                var height = heightFraction * heightMetres;
                // Wide foot, then a slow taper.
                var radius = baseRadius * Mathf.Lerp(1f, TopRadiusFraction, Mathf.Pow(heightFraction, 0.6f));
                var leanOffset = new Vector2(
                    Mathf.PerlinNoise(noiseOffset, height * 0.05f) - 0.5f,
                    Mathf.PerlinNoise(noiseOffset + 3f, height * 0.05f) - 0.5f) * baseRadius * 0.8f * heightFraction;

                for (var sectionIndex = 0; sectionIndex <= RingSections; sectionIndex++)
                {
                    var angleFraction = sectionIndex / (float)RingSections;
                    var angle = angleFraction * 2f * Mathf.PI;
                    var bump = (Mathf.PerlinNoise(noiseOffset + Mathf.Cos(angle) * 1.5f + height * 0.3f,
                                                  noiseOffset + Mathf.Sin(angle) * 1.5f) - 0.5f) * 2f * BumpAmount;
                    var bumpedRadius = radius * (1f + bump);
                    vertices.Add(new Vector3(Mathf.Cos(angle) * bumpedRadius + leanOffset.x, height,
                                             Mathf.Sin(angle) * bumpedRadius + leanOffset.y));
                    var circumference = 2f * Mathf.PI * baseRadius;
                    uvs.Add(new Vector2(angleFraction * circumference, height) / TextureMetresPerRepeat);
                }
            }

            var verticesPerRing = RingSections + 1;
            for (var ringIndex = 0; ringIndex < ringCount - 1; ringIndex++)
                AddRingStrip(triangles, ringIndex * verticesPerRing, (ringIndex + 1) * verticesPerRing, facesInward: false);

            // Open top: a crater (the vent). A flat rim goes in, a wall goes down, a small floor closes it.
            var lastRingStart = (ringCount - 1) * verticesPerRing;
            var topCentre = Vector3.zero;
            for (var sectionIndex = 0; sectionIndex < RingSections; sectionIndex++)
                topCentre += vertices[lastRingStart + sectionIndex];
            topCentre /= RingSections;
            var craterDepth = Mathf.Min(CraterMaxDepthMetres, heightMetres * 0.3f);

            var craterTopStart = vertices.Count;
            AddScaledRing(vertices, uvs, lastRingStart, topCentre, CraterTopRadiusFraction, 0f);
            var craterBottomStart = vertices.Count;
            AddScaledRing(vertices, uvs, lastRingStart, topCentre, CraterBottomRadiusFraction, craterDepth);
            var craterFloorIndex = vertices.Count;
            vertices.Add(topCentre - new Vector3(0f, craterDepth, 0f));
            uvs.Add(Vector2.zero);

            AddRingStrip(triangles, lastRingStart, craterTopStart, facesInward: false);   // rim, faces up
            AddRingStrip(triangles, craterBottomStart, craterTopStart, facesInward: true); // crater wall
            for (var sectionIndex = 0; sectionIndex < RingSections; sectionIndex++)
                triangles.AddRange(new[] { craterBottomStart + sectionIndex, craterFloorIndex, craterBottomStart + sectionIndex + 1 });
            var mesh = new Mesh { name = "ChimneySpire" };
            mesh.SetVertices(vertices);
            mesh.SetUVs(0, uvs);
            mesh.SetTriangles(triangles, 0);
            mesh.RecalculateNormals();
            mesh.RecalculateTangents();
            mesh.RecalculateBounds();
            return mesh;
        }

        // Two rings of RingSections + 1 vertices joined by quads. Seen from outside, clockwise = front face in Unity;
        // facesInward flips it, for a wall seen from inside the crater.
        private static void AddRingStrip(List<int> triangles, int lowerRingStart, int upperRingStart, bool facesInward)
        {
            for (var sectionIndex = 0; sectionIndex < RingSections; sectionIndex++)
            {
                var lowerLeft = lowerRingStart + sectionIndex;
                var upperLeft = upperRingStart + sectionIndex;
                if (facesInward)
                {
                    triangles.AddRange(new[] { lowerLeft, lowerLeft + 1, upperLeft });
                    triangles.AddRange(new[] { lowerLeft + 1, upperLeft + 1, upperLeft });
                }
                else
                {
                    triangles.AddRange(new[] { lowerLeft, upperLeft, lowerLeft + 1 });
                    triangles.AddRange(new[] { lowerLeft + 1, upperLeft, upperLeft + 1 });
                }
            }
        }

        // Copy of an existing ring, pulled towards the centre and moved down.
        private static void AddScaledRing(List<Vector3> vertices, List<Vector2> uvs, int sourceRingStart,
            Vector3 centre, float radiusFraction, float depthMetres)
        {
            for (var sectionIndex = 0; sectionIndex <= RingSections; sectionIndex++)
            {
                var source = vertices[sourceRingStart + sectionIndex];
                vertices.Add(centre + (source - centre) * radiusFraction - new Vector3(0f, depthMetres, 0f));
                uvs.Add(uvs[sourceRingStart + sectionIndex] + new Vector2(0f, depthMetres / TextureMetresPerRepeat));
            }
        }

        private Material CreateMineralMaterial()
        {
            var material = new Material(Shader.Find("Universal Render Pipeline/Lit")) { enableInstancing = true };
            var albedo = Resources.Load<Texture2D>($"{TextureResourceFolder}hydrothermal_albedo");
            var normal = Resources.Load<Texture2D>($"{TextureResourceFolder}hydrothermal_normal");
            if (albedo != null) material.SetTexture("_BaseMap", albedo);
            else material.SetColor("_BaseColor", SimpleColumnColor);
            if (normal != null)
            {
                material.SetTexture("_BumpMap", normal);
                material.EnableKeyword("_NORMALMAP");
            }
            material.SetFloat("_Smoothness", 0.2f);
            _ownedAssets.Add(material);
            return material;
        }

        // Black smoke: hot water full of minerals, rising and spreading.
        private void AddSmoke(Transform parent, Vector3 localPosition, float ventRadius)
        {
            var smokeObject = new GameObject("Smoke");
            smokeObject.transform.SetParent(parent, false);
            smokeObject.transform.localPosition = localPosition;
            smokeObject.transform.localRotation = Quaternion.Euler(-90f, 0f, 0f); // emit upwards

            var particles = smokeObject.AddComponent<ParticleSystem>();
            particles.Stop(true, ParticleSystemStopBehavior.StopEmittingAndClear);

            var main = particles.main;
            main.duration = 5f;
            main.loop = true;
            main.startLifetime = new ParticleSystem.MinMaxCurve(6f, 9f);
            main.startSpeed = new ParticleSystem.MinMaxCurve(1.2f, 2f);
            main.startSize = new ParticleSystem.MinMaxCurve(ventRadius * 1.5f, ventRadius * 2.5f);
            main.startRotation = new ParticleSystem.MinMaxCurve(0f, 2f * Mathf.PI);
            main.startColor = SmokeColor;
            main.simulationSpace = ParticleSystemSimulationSpace.World;
            main.maxParticles = 150;
            main.prewarm = true;

            var emission = particles.emission;
            emission.rateOverTime = 14f;

            var shape = particles.shape;
            shape.shapeType = ParticleSystemShapeType.Cone;
            shape.angle = 8f;
            shape.radius = ventRadius * 0.5f;

            var sizeOverLifetime = particles.sizeOverLifetime;
            sizeOverLifetime.enabled = true;
            sizeOverLifetime.size = new ParticleSystem.MinMaxCurve(1f, AnimationCurve.Linear(0f, 0.6f, 1f, 3f));

            var colorOverLifetime = particles.colorOverLifetime;
            colorOverLifetime.enabled = true;
            var fade = new Gradient();
            fade.SetKeys(new[] { new GradientColorKey(Color.white, 0f), new GradientColorKey(Color.white, 1f) },
                         new[] { new GradientAlphaKey(0f, 0f), new GradientAlphaKey(1f, 0.1f), new GradientAlphaKey(0f, 1f) });
            colorOverLifetime.color = fade;

            var renderer = smokeObject.GetComponent<ParticleSystemRenderer>();
            renderer.sharedMaterial = CreateSmokeMaterial();
            particles.Play();
        }

        private Material CreateSmokeMaterial()
        {
            var material = new Material(Shader.Find("Universal Render Pipeline/Particles/Unlit"));
            material.SetFloat("_Surface", 1f); // transparent
            material.SetFloat("_Blend", 0f);   // alpha blending
            material.SetFloat("_SrcBlend", (float)UnityEngine.Rendering.BlendMode.SrcAlpha);
            material.SetFloat("_DstBlend", (float)UnityEngine.Rendering.BlendMode.OneMinusSrcAlpha);
            material.SetFloat("_ZWrite", 0f);
            material.EnableKeyword("_SURFACE_TYPE_TRANSPARENT");
            material.renderQueue = (int)UnityEngine.Rendering.RenderQueue.Transparent;
            var puffTexture = CreateSoftPuffTexture();
            material.SetTexture("_BaseMap", puffTexture);
            material.SetColor("_BaseColor", Color.white);
            _ownedAssets.Add(puffTexture);
            _ownedAssets.Add(material);
            return material;
        }

        // Round, soft-edged dot: without it each smoke particle would be a square.
        private static Texture2D CreateSoftPuffTexture()
        {
            const int size = 64;
            var texture = new Texture2D(size, size, TextureFormat.RGBA32, false) { wrapMode = TextureWrapMode.Clamp };
            for (var row = 0; row < size; row++)
            {
                for (var column = 0; column < size; column++)
                {
                    var offset = new Vector2(column + 0.5f - size / 2f, row + 0.5f - size / 2f) / (size / 2f);
                    var alpha = Mathf.Clamp01(1f - offset.magnitude);
                    texture.SetPixel(column, row, new Color(1f, 1f, 1f, alpha * alpha));
                }
            }
            texture.Apply();
            return texture;
        }

        // ---------- Simple ----------

        private void BuildSimple(float heightMetres, float baseRadius)
        {
            _simple = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            Destroy(_simple.GetComponent<Collider>());
            _simple.name = "Simple";
            _simple.transform.SetParent(transform, false);
            var diameter = 2f * baseRadius * SimpleColumnRadiusFraction;
            // The Unity cylinder is 2 units tall, centred on its pivot.
            _simple.transform.localScale = new Vector3(diameter, heightMetres / 2f, diameter);
            _simple.transform.localPosition = new Vector3(0f, heightMetres / 2f, 0f);
            var material = new Material(Shader.Find("Universal Render Pipeline/Lit")) { color = SimpleColumnColor };
            _simple.GetComponent<Renderer>().sharedMaterial = material;
            _ownedAssets.Add(material);
        }

        private void OnDestroy()
        {
            DetailedView.Changed -= ApplyView;
            foreach (var asset in _ownedAssets) Destroy(asset);
        }
    }
}
