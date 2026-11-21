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
    /// (see ADR-0006). Turning the component off (UI check box) gives back the scene as it was.
    /// Artistic choice: at the depth of the Gakkel Ridge (about 4 km) no sunlight is left. The light
    /// and caustics here are for the look, like in a documentary.
    /// </summary>
    public class UnderwaterAtmosphere : MonoBehaviour
    {
        [Header("Fog (exponential squared, gets denser and darker near the seabed)")]
        [SerializeField] private Color openWaterColor = new(0.12f, 0.36f, 0.48f);
        [SerializeField] private Color nearSeabedColor = new(0.07f, 0.22f, 0.30f);
        [SerializeField] private float openWaterFogDensity = 0.004f;
        [SerializeField] private float nearSeabedFogDensity = 0.007f;
        [SerializeField] private float simpleViewFogDensity = 0.0025f;
        [Tooltip("Height of the camera above the seabed (m) from which the water stops getting lighter.")]
        [SerializeField] private float depthFadeHeightMetres = 120f;

        [Header("Light from above")]
        [SerializeField] private Color waterLightColor = new(0.75f, 0.92f, 1f);
        [SerializeField] private float openWaterLightIntensity = 2.4f;
        [SerializeField] private float nearSeabedLightIntensity = 1.7f;
        [SerializeField] private float simpleViewLightIntensity = 2f;

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
        private Volume _volume;
        private VolumeProfile _profile;
        private Bloom _bloom;
        private Vignette _vignette;
        private SceneLook _sceneLookBefore;
        private bool _isSetUp;
        private bool _isDetailed;
        private float _depthFactor = 1f; // 1 = open water, 0 = on the seabed

        public static UnderwaterAtmosphere Create(SwarmVisualizer visualizer)
        {
            var atmosphereObject = new GameObject("UnderwaterAtmosphere");
            var atmosphere = atmosphereObject.AddComponent<UnderwaterAtmosphere>();
            atmosphere._visualizer = visualizer;
            return atmosphere;
        }

        // Start runs once, after the first OnEnable: build everything, then switch it on.
        private void Start()
        {
            _camera = Camera.main;
            _sunLight = FindDirectionalLight();
            if (_sunLight != null)
            {
                // No "??" here: a missing Unity component is a fake null that "??" does not see.
                if (!_sunLight.TryGetComponent(out _sunLightData))
                    _sunLightData = _sunLight.gameObject.AddComponent<UniversalAdditionalLightData>();
                _causticsTexture = CausticsTexture.Create(CausticsTexturePixels, CausticsCellsPerSide, CausticsSeed);
            }
            if (_camera != null) _marineSnow = MarineSnow.Spawn(_camera.transform, marineSnowMaxFlakes);
            CreatePostProcessing();
            _sceneLookBefore = SceneLook.Capture(_camera, _sunLight);
            DetailedView.Changed += ApplyView;
            _isSetUp = true;
            TurnOn();
        }

        private void OnEnable()
        {
            if (_isSetUp) TurnOn();
        }

        private void OnDisable()
        {
            if (_isSetUp) TurnOff();
        }

        private void TurnOn()
        {
            RenderSettings.fog = true;
            RenderSettings.fogMode = FogMode.ExponentialSquared;
            RenderSettings.ambientMode = AmbientMode.Trilight;
            if (_camera != null) _camera.clearFlags = CameraClearFlags.SolidColor; // no sky under the sea
            if (_sunLight != null)
            {
                _sunLight.color = waterLightColor;
                _sunLightData.lightCookieSize = Vector2.one * causticsTileSizeMetres;
            }
            _volume.enabled = true;
            _depthFactor = TargetDepthFactor();
            ApplyView(DetailedView.IsOn);
        }

        private void TurnOff()
        {
            _sceneLookBefore.Restore(_camera, _sunLight);
            _volume.enabled = false;
            if (_marineSnow != null) _marineSnow.SetVisible(false);
        }

        private void Update()
        {
            _depthFactor = Mathf.MoveTowards(_depthFactor, TargetDepthFactor(), DepthFactorChangePerSecond * Time.deltaTime);
            // The simple view keeps the open water colour: lighter, easier to read.
            var waterColor = _isDetailed ? Color.Lerp(nearSeabedColor, openWaterColor, _depthFactor) : openWaterColor;

            RenderSettings.fogColor = waterColor;
            RenderSettings.fogDensity = _isDetailed
                ? Mathf.Lerp(nearSeabedFogDensity, openWaterFogDensity, _depthFactor)
                : simpleViewFogDensity;
            if (_camera != null) _camera.backgroundColor = waterColor;

            // Light comes from the surface: blue-green from above, darker from below.
            RenderSettings.ambientSkyColor = waterColor * 3f;
            RenderSettings.ambientEquatorColor = waterColor * 2f;
            RenderSettings.ambientGroundColor = waterColor * 1f;

            if (_sunLight != null)
            {
                _sunLight.intensity = _isDetailed
                    ? Mathf.Lerp(nearSeabedLightIntensity, openWaterLightIntensity, _depthFactor)
                    : simpleViewLightIntensity;
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
            if (!enabled) return; // the atmosphere is off: keep the scene as it was
            if (_sunLight != null) _sunLight.cookie = isDetailed ? _causticsTexture : null;
            if (_marineSnow != null) _marineSnow.SetVisible(isDetailed);
            // Set to 0 rather than turned off: otherwise the Global Volume of the scene would apply its own.
            _bloom.intensity.Override(isDetailed ? 0.8f : 0f);
            _vignette.intensity.Override(isDetailed ? 0.25f : 0f);
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
            colorAdjustments.colorFilter.Override(new Color(0.88f, 0.98f, 1f)); // red is the first colour water absorbs
            colorAdjustments.saturation.Override(-5f);
            colorAdjustments.contrast.Override(8f);
            colorAdjustments.postExposure.Override(0.5f);

            _bloom = _profile.Add<Bloom>(true);
            _bloom.threshold.Override(0.9f);
            _bloom.scatter.Override(0.7f);
            _bloom.tint.Override(new Color(0.75f, 0.92f, 1f));

            _vignette = _profile.Add<Vignette>(true);
            _vignette.color.Override(new Color(0f, 0.05f, 0.08f));
            _vignette.smoothness.Override(0.5f);

            var tonemapping = _profile.Add<Tonemapping>(true);
            tonemapping.mode.Override(TonemappingMode.ACES);

            _volume = gameObject.AddComponent<Volume>();
            _volume.isGlobal = true;
            _volume.priority = VolumePriority;
            _volume.sharedProfile = _profile;
        }

        private static Light FindDirectionalLight()
        {
            if (RenderSettings.sun != null) return RenderSettings.sun;
            foreach (var light in FindObjectsByType<Light>())
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

        /// <summary>The fog, ambient light, camera background and light of the scene before the atmosphere.</summary>
        private readonly struct SceneLook
        {
            private readonly bool _fog;
            private readonly FogMode _fogMode;
            private readonly Color _fogColor;
            private readonly float _fogDensity;
            private readonly AmbientMode _ambientMode;
            private readonly Color _ambientSkyColor;
            private readonly Color _ambientEquatorColor;
            private readonly Color _ambientGroundColor;
            private readonly CameraClearFlags _cameraClearFlags;
            private readonly Color _cameraBackgroundColor;
            private readonly Color _lightColor;
            private readonly float _lightIntensity;
            private readonly Texture _lightCookie;

            private SceneLook(Camera camera, Light light)
            {
                _fog = RenderSettings.fog;
                _fogMode = RenderSettings.fogMode;
                _fogColor = RenderSettings.fogColor;
                _fogDensity = RenderSettings.fogDensity;
                _ambientMode = RenderSettings.ambientMode;
                _ambientSkyColor = RenderSettings.ambientSkyColor;
                _ambientEquatorColor = RenderSettings.ambientEquatorColor;
                _ambientGroundColor = RenderSettings.ambientGroundColor;
                _cameraClearFlags = camera != null ? camera.clearFlags : CameraClearFlags.Skybox;
                _cameraBackgroundColor = camera != null ? camera.backgroundColor : Color.black;
                _lightColor = light != null ? light.color : Color.white;
                _lightIntensity = light != null ? light.intensity : 1f;
                _lightCookie = light != null ? light.cookie : null;
            }

            public static SceneLook Capture(Camera camera, Light light) => new(camera, light);

            public void Restore(Camera camera, Light light)
            {
                RenderSettings.fog = _fog;
                RenderSettings.fogMode = _fogMode;
                RenderSettings.fogColor = _fogColor;
                RenderSettings.fogDensity = _fogDensity;
                RenderSettings.ambientMode = _ambientMode;
                RenderSettings.ambientSkyColor = _ambientSkyColor;
                RenderSettings.ambientEquatorColor = _ambientEquatorColor;
                RenderSettings.ambientGroundColor = _ambientGroundColor;
                if (camera != null)
                {
                    camera.clearFlags = _cameraClearFlags;
                    camera.backgroundColor = _cameraBackgroundColor;
                }
                if (light != null)
                {
                    light.color = _lightColor;
                    light.intensity = _lightIntensity;
                    light.cookie = _lightCookie;
                }
            }
        }
    }
}
