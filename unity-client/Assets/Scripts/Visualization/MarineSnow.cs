using UnityEngine;

namespace Gakkel.Swarm.Unity
{
    /// <summary>
    /// Marine snow: tiny flakes of organic matter that slowly sink in the deep sea. They float around
    /// the camera and show that the water moves, which makes the scene read as "underwater" at once.
    /// The flakes stay in the world (they do not follow the camera), so they drift past when it moves.
    /// Detailed view only (see <see cref="UnderwaterAtmosphere"/>).
    /// </summary>
    public class MarineSnow : MonoBehaviour
    {
        private const float LifetimeSeconds = 14f;
        private const float SinkSpeedMetresPerSecond = 0.08f;
        private static readonly Vector3 VolumeAroundCameraMetres = new(70f, 40f, 70f);
        private static readonly Color FlakeColor = new(0.85f, 0.95f, 1f, 0.55f);

        private ParticleSystem _particles;
        private Material _material;
        private Texture2D _flakeTexture;

        public static MarineSnow Spawn(Transform cameraTransform, int maxFlakes)
        {
            var snowObject = new GameObject("MarineSnow");
            snowObject.transform.SetParent(cameraTransform, false);
            var marineSnow = snowObject.AddComponent<MarineSnow>();
            marineSnow.Build(maxFlakes);
            return marineSnow;
        }

        public void SetVisible(bool isVisible)
        {
            if (isVisible && !_particles.isPlaying) _particles.Play();
            if (!isVisible) _particles.Stop(true, ParticleSystemStopBehavior.StopEmittingAndClear);
        }

        private void Build(int maxFlakes)
        {
            _particles = gameObject.AddComponent<ParticleSystem>();
            _particles.Stop(true, ParticleSystemStopBehavior.StopEmittingAndClear);

            var main = _particles.main;
            main.duration = LifetimeSeconds;
            main.loop = true;
            main.prewarm = true;
            main.startLifetime = LifetimeSeconds;
            main.startSpeed = 0f;
            main.startSize = new ParticleSystem.MinMaxCurve(0.04f, 0.14f);
            main.startColor = FlakeColor;
            main.simulationSpace = ParticleSystemSimulationSpace.World;
            main.maxParticles = maxFlakes;

            var emission = _particles.emission;
            emission.rateOverTime = maxFlakes / LifetimeSeconds;

            var shape = _particles.shape;
            shape.shapeType = ParticleSystemShapeType.Box;
            shape.scale = VolumeAroundCameraMetres;

            var velocity = _particles.velocityOverLifetime;
            velocity.enabled = true;
            velocity.space = ParticleSystemSimulationSpace.World;
            velocity.x = new ParticleSystem.MinMaxCurve(0f);
            velocity.y = new ParticleSystem.MinMaxCurve(-SinkSpeedMetresPerSecond);
            velocity.z = new ParticleSystem.MinMaxCurve(0f);

            // Slow swirls, like a weak current.
            var noise = _particles.noise;
            noise.enabled = true;
            noise.strength = 0.15f;
            noise.frequency = 0.25f;
            noise.scrollSpeed = 0.05f;

            // Fade in and out, so flakes do not pop.
            var colorOverLifetime = _particles.colorOverLifetime;
            colorOverLifetime.enabled = true;
            var fade = new Gradient();
            fade.SetKeys(new[] { new GradientColorKey(Color.white, 0f), new GradientColorKey(Color.white, 1f) },
                         new[] { new GradientAlphaKey(0f, 0f), new GradientAlphaKey(1f, 0.15f),
                                 new GradientAlphaKey(1f, 0.85f), new GradientAlphaKey(0f, 1f) });
            colorOverLifetime.color = fade;

            var particleRenderer = GetComponent<ParticleSystemRenderer>();
            particleRenderer.renderMode = ParticleSystemRenderMode.Billboard;
            particleRenderer.sharedMaterial = CreateMaterial();
            _particles.Play();
        }

        private Material CreateMaterial()
        {
            _material = new Material(Shader.Find("Universal Render Pipeline/Particles/Unlit"));
            _material.SetFloat("_Surface", 1f); // transparent
            _material.SetFloat("_Blend", 0f);   // alpha blending
            _material.SetFloat("_SrcBlend", (float)UnityEngine.Rendering.BlendMode.SrcAlpha);
            _material.SetFloat("_DstBlend", (float)UnityEngine.Rendering.BlendMode.OneMinusSrcAlpha);
            _material.SetFloat("_ZWrite", 0f);
            _material.EnableKeyword("_SURFACE_TYPE_TRANSPARENT");
            _material.renderQueue = (int)UnityEngine.Rendering.RenderQueue.Transparent;
            _flakeTexture = CreateFlakeTexture();
            _material.SetTexture("_BaseMap", _flakeTexture);
            _material.SetColor("_BaseColor", Color.white);
            return _material;
        }

        // Small round dot with a soft edge.
        private static Texture2D CreateFlakeTexture()
        {
            const int size = 32;
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

        private void OnDestroy()
        {
            Destroy(_material);
            Destroy(_flakeTexture);
        }
    }
}
