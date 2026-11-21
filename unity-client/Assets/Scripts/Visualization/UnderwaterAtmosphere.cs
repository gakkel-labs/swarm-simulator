using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.Rendering.Universal;

namespace Gakkel.Swarm.Unity
{
    /// <summary>
    /// Underwater look of the scene: blue fog, light from above that gets weaker near the seabed,
    /// caustics, marine snow and colour grading. Created by <see cref="SwarmVisualizer"/>, so there is
    /// nothing to add in the scene. The values below can be tuned in the Inspector during Play.
    /// Detailed view: everything. Simple view: a lighter fog only, so the swarm stays easy to read
    /// (see ADR-0006).
    /// Artistic choice: at the depth of the Gakkel Ridge (about 4 km) no sunlight is left. The light
    /// and caustics here are for the look, like in a documentary.
    /// </summary>
    public class UnderwaterAtmosphere : MonoBehaviour
    {
        [Header("Fog (exponential squared, gets denser and darker near the seabed)")]
        [SerializeField] private Color openWaterColor = new(0.06f, 0.24f, 0.34f);
        [SerializeField] private Color nearSeabedColor = new(0.02f, 0.09f, 0.14f);
        [SerializeField] private float openWaterFogDensity = 0.006f;
        [SerializeField] private float nearSeabedFogDensity = 0.011f;
        [SerializeField] private float simpleViewFogDensity = 0.003f;
        [Tooltip("Height of the camera above the seabed (m) from which the water stops getting lighter.")]
        [SerializeField] private float depthFadeHeightMetres = 120f;

        [Header("Light from above")]
        [SerializeField] private Color waterLightColor = new(0.65f, 0.88f, 1f);
        [SerializeField] private float openWaterLightIntensity = 1.6f;
        [SerializeField] private float nearSeabedLightIntensity = 0.7f;

        [Header("Caustics (detailed view)")]
        [SerializeField] private float causticsTileSizeMetres = 14f;
        [SerializeField] private Vector2 causticsDriftMetresPerSecond = new(0.5f, 0.3f);

        [Header("Marine snow (detailed view)")]
        [SerializeField] private int marineSnowMaxFlakes = 1500;

        private const int CausticsTexturePixels = 256;
        private const int CausticsCellsPerSide = 6;
        private const int CausticsSeed = 74;
        private const float DepthFactorChangePerSecond = 0.5f;
        private const int VolumePriority = 10; // above the Global Volume of the scene

        private SwarmVisualizer _visualizer;
        private Camera _camera;
        private Light _sunLight;
        private UniversalAdditionalLightData _sunLightData;
        private Texture2D _causticsTexture;
        private Vector2 _causticsOffset;
        private MarineSnow _marineSnow;
        private VolumeProfile _profile;
        private Bloom _bloom;
        private Vignette _vignette;
        private bool _isDetailed;
        private float _depthFactor = 1f; // 1 = open water, 0 = on the seabed

        public static UnderwaterAtmosphere Create(SwarmVisualizer visualizer)
        {
            var atmosphereObject = new GameObject("UnderwaterAtmosphere");
            var atmosphere = atmosphereObject.AddComponent<UnderwaterAtmosphere>();
            atmosphere._visualizer = visualizer;
            return atmosphere;
        }

        private void Start()
        {
            _camera = Camera.main;
            _sunLight = FindDirectionalLight();
            if (_sunLight != null)
            {
                _sunLight.color = waterLightColor;
                // No "??" here: a missing Unity component is a fake null that "??" does not see.
                if (!_sunLight.TryGetComponent(out _sunLightData))
                    _sunLightData = _sunLight.gameObject.AddComponent<UniversalAdditionalLightData>();
                _causticsTexture = CausticsTexture.Create(CausticsTexturePixels, CausticsCellsPerSide, CausticsSeed);
                _sunLightData.lightCookieSize = Vector2.one * causticsTileSizeMetres;
            }
            if (_camera != null)
            {
                _camera.clearFlags = CameraClearFlags.SolidColor; // no sky under the sea
                _marineSnow = MarineSnow.Spawn(_camera.transform, marineSnowMaxFlakes);
            }

            RenderSettings.fog = true;
            RenderSettings.fogMode = FogMode.ExponentialSquared;
            RenderSettings.ambientMode = AmbientMode.Trilight;
            CreatePostProcessing();

            _depthFactor = TargetDepthFactor();
            ApplyView(DetailedView.IsOn);
            DetailedView.Changed += ApplyView;
        }

        private void Update()
        {
            _depthFactor = Mathf.MoveTowards(_depthFactor, TargetDepthFactor(), DepthFactorChangePerSecond * Time.deltaTime);
            var waterColor = Color.Lerp(nearSeabedColor, openWaterColor, _depthFactor);

            RenderSettings.fogColor = waterColor;
            RenderSettings.fogDensity = _isDetailed
                ? Mathf.Lerp(nearSeabedFogDensity, openWaterFogDensity, _depthFactor)
                : simpleViewFogDensity;
            if (_camera != null) _camera.backgroundColor = waterColor;

            // Light comes from the surface: blue-green from above, dark from below.
            RenderSettings.ambientSkyColor = waterColor * 2.2f;
            RenderSettings.ambientEquatorColor = waterColor * 1.2f;
            RenderSettings.ambientGroundColor = waterColor * 0.4f;

            if (_sunLight != null)
            {
                _sunLight.intensity = _isDetailed
                    ? Mathf.Lerp(nearSeabedLightIntensity, openWaterLightIntensity, _depthFactor)
                    : openWaterLightIntensity;
                if (_isDetailed)
                {
                    _causticsOffset += causticsDriftMetresPerSecond * Time.deltaTime;
                    _causticsOffset.x %= causticsTileSizeMetres;
                    _causticsOffset.y %= causticsTileSizeMetres;
                    _sunLightData.lightCookieOffset = _causticsOffset;
                }
            }
        }

        private void ApplyView(bool isDetailed)
        {
            _isDetailed = isDetailed;
            if (_sunLight != null) _sunLight.cookie = isDetailed ? _causticsTexture : null;
            if (_marineSnow != null) _marineSnow.SetVisible(isDetailed);
            // Set to 0 rather than turned off: otherwise the Global Volume of the scene would apply its own.
            _bloom.intensity.Override(isDetailed ? 0.9f : 0f);
            _vignette.intensity.Override(isDetailed ? 0.35f : 0f);
        }

        // 1 high above the seabed, 0 on it.
        private float TargetDepthFactor()
        {
            if (_camera == null || _visualizer == null) return 1f;
            var cameraPosition = _camera.transform.position;
            var heightAboveSeabed = cameraPosition.y - _visualizer.SeabedHeightAt(cameraPosition);
            return Mathf.Clamp01(heightAboveSeabed / depthFadeHeightMetres);
        }

        // Post-processing: a Volume made by code, applied over the one of the scene.
        private void CreatePostProcessing()
        {
            _profile = ScriptableObject.CreateInstance<VolumeProfile>();

            var colorAdjustments = _profile.Add<ColorAdjustments>(true);
            colorAdjustments.colorFilter.Override(new Color(0.82f, 0.96f, 1f)); // red is the first colour water absorbs
            colorAdjustments.saturation.Override(-10f);
            colorAdjustments.contrast.Override(12f);
            colorAdjustments.postExposure.Override(0.2f);

            _bloom = _profile.Add<Bloom>(true);
            _bloom.threshold.Override(0.85f);
            _bloom.scatter.Override(0.75f);
            _bloom.tint.Override(new Color(0.7f, 0.9f, 1f));

            _vignette = _profile.Add<Vignette>(true);
            _vignette.color.Override(new Color(0f, 0.05f, 0.08f));
            _vignette.smoothness.Override(0.5f);

            var tonemapping = _profile.Add<Tonemapping>(true);
            tonemapping.mode.Override(TonemappingMode.ACES);

            var volume = gameObject.AddComponent<Volume>();
            volume.isGlobal = true;
            volume.priority = VolumePriority;
            volume.sharedProfile = _profile;
        }

        private static Light FindDirectionalLight()
        {
            if (RenderSettings.sun != null) return RenderSettings.sun;
            foreach (var light in FindObjectsByType<Light>(FindObjectsSortMode.None))
                if (light.type == LightType.Directional) return light;
            return null;
        }

        private void OnDestroy()
        {
            DetailedView.Changed -= ApplyView;
            if (_profile != null)
            {
                foreach (var component in _profile.components) Destroy(component);
                Destroy(_profile);
            }
            Destroy(_causticsTexture);
        }
    }
}
