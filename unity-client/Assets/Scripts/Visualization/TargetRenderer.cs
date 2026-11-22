using Gakkel.Swarm.Contracts.V1;
using UnityEngine;

namespace Gakkel.Swarm.Unity
{
    /// <summary>
    /// Shows the search target as a wreck lying on the seabed, under the target position.
    /// Detailed view: the 3D model, by default an old wooden ship (Assets/Models/ShipWreck.glb, made by
    /// tools/models/make_ship_wreck.py). An aircraft wreck (AircraftWreck.glb) can be set in the Inspector.
    /// Simple view: a plain ship shape made of boxes and a cylinder.
    /// When a drone finds the target, a green ring appears around the wreck (it blinks, then stays).
    /// The simulation target is still a point in the water: the wreck is drawn on the seabed below it.
    /// </summary>
    public class TargetRenderer : MonoBehaviour
    {
        private static readonly Color FoundColor = new(0.2f, 1f, 0.3f, 0.45f);
        private static readonly Color BlinkColor = new(1f, 1f, 1f, 0.6f);
        private static readonly Color SimpleWreckColor = new(0.6f, 0.6f, 0.58f);
        private const float BlinkDuration = 5f;
        private const float BlinkFrequency = 10f;
        private const float FoundRingRadiusMetres = 24f;
        private const float WreckHeadingDegrees = 35f;

        [Tooltip("Wreck model: Assets/Models/ShipWreck.glb (default) or AircraftWreck.glb. Empty = simple shape only.")]
        [SerializeField] private GameObject wreckModel;
        [Tooltip("Texture for the deck and masts (model material Wood_Deck). Any wood texture works. " +
                 "Default: Assets/Models/WoodPlanks.png. Empty = plain colour from the model.")]
        [SerializeField] private Texture2D woodTexture;
        [Tooltip("Colour multiplied with the wood texture: white = texture as it is, grey = darker.")]
        [SerializeField] private Color woodTint = new(0.85f, 0.85f, 0.85f);
        [Tooltip("Gives the seabed height under the target. Found automatically if empty.")]
        [SerializeField] private SwarmVisualizer visualizer;

        private GameObject _wreck;
        private GameObject _detailedWreck;
        private GameObject _simpleWreck;
        private GameObject _foundRing;
        private Material _foundRingMaterial;
        private Material _simpleWreckMaterial;
        private Material _woodMaterial;
        private float _blinkStartTime;
        private bool _isBlinking;

        public bool IsPlaced               => _wreck.activeSelf;
        public bool IsFound                { get; private set; }
        public float ElapsedSimSeconds     { get; private set; }
        public string FoundByAgentId       { get; private set; }
        public float FoundAtElapsedSeconds { get; private set; }

#if UNITY_EDITOR
        private const string DefaultWreckModelPath = "Assets/Models/ShipWreck.glb";
        private const string DefaultWoodTexturePath = "Assets/Models/WoodPlanks.png";

        // Editor only: if nobody has set the wreck model or the wood texture in the Inspector yet,
        // pick the ones in the repo. Save the scene (Ctrl+S) once to keep them.
        private void OnValidate()
        {
            if (wreckModel == null)
                wreckModel = UnityEditor.AssetDatabase.LoadAssetAtPath<GameObject>(DefaultWreckModelPath);
            if (woodTexture == null)
                woodTexture = UnityEditor.AssetDatabase.LoadAssetAtPath<Texture2D>(DefaultWoodTexturePath);
        }
#endif

        private void Awake()
        {
            if (visualizer == null) visualizer = FindAnyObjectByType<SwarmVisualizer>();
            var litShader = Shader.Find("Universal Render Pipeline/Lit");

            _wreck = new GameObject("SearchTarget_Wreck");
            _wreck.transform.rotation = Quaternion.Euler(0f, WreckHeadingDegrees, 0f);

            if (wreckModel != null)
            {
                _detailedWreck = Instantiate(wreckModel, _wreck.transform, false);
                _detailedWreck.name = "Detailed";
                foreach (var wreckCollider in _detailedWreck.GetComponentsInChildren<Collider>())
                    Destroy(wreckCollider);
                if (woodTexture != null) ApplyWoodTexture(_detailedWreck, litShader);
            }

            _simpleWreckMaterial = new Material(litShader) { color = SimpleWreckColor };
            _simpleWreck = BuildSimpleWreck(_simpleWreckMaterial);
            _simpleWreck.transform.SetParent(_wreck.transform, false);

            _foundRingMaterial = RenderingUtils.MakeTransparentMaterial(litShader, FoundColor);
            _foundRing = CreatePart(PrimitiveType.Cylinder, _foundRingMaterial, _wreck.transform,
                new Vector3(0f, 0.3f, 0f), new Vector3(2f * FoundRingRadiusMetres, 0.05f, 2f * FoundRingRadiusMetres));
            _foundRing.name = "FoundRing";
            _foundRing.SetActive(false);

            ApplyView(DetailedView.IsOn);
            DetailedView.Changed += ApplyView;
            _wreck.SetActive(false);
        }

        // The model brings its own materials (plain colours). Here we swap the one named Wood_Deck
        // for a URP Lit material with the wood texture. The model has UVs where 1 repeat = 2 m.
        private void ApplyWoodTexture(GameObject model, Shader litShader)
        {
            _woodMaterial = new Material(litShader) { name = "Wood_Deck_Textured" };
            _woodMaterial.SetTexture("_BaseMap", woodTexture);
            _woodMaterial.SetColor("_BaseColor", woodTint);
            _woodMaterial.SetFloat("_Smoothness", 0.15f);
            foreach (var modelRenderer in model.GetComponentsInChildren<Renderer>())
            {
                var materials = modelRenderer.sharedMaterials;
                for (int materialIndex = 0; materialIndex < materials.Length; materialIndex++)
                {
                    if (materials[materialIndex] != null && materials[materialIndex].name.StartsWith("Wood_Deck"))
                        materials[materialIndex] = _woodMaterial;
                }
                modelRenderer.sharedMaterials = materials;
            }
        }

        private void ApplyView(bool isDetailed)
        {
            var showDetailed = isDetailed && _detailedWreck != null;
            if (_detailedWreck != null) _detailedWreck.SetActive(showDetailed);
            _simpleWreck.SetActive(!showDetailed);
        }

        private void Update()
        {
            if (!_isBlinking) return;
            float elapsed = Time.time - _blinkStartTime;
            if (elapsed > BlinkDuration)
            {
                _foundRingMaterial.color = FoundColor;
                _isBlinking = false;
                return;
            }
            _foundRingMaterial.color = IsInBlinkFlash(_blinkStartTime) ? BlinkColor : FoundColor;
        }

        public void Apply(SearchStatus searchStatus)
        {
            if (searchStatus == null || !searchStatus.TargetPlaced)
            {
                _wreck.SetActive(false);
                _foundRing.SetActive(false);
                IsFound = false;
                return;
            }

            ElapsedSimSeconds = searchStatus.ElapsedSimS;
            var targetPosition = CoordinateUtils.NedToUnity(searchStatus.TargetPosition);
            if (visualizer != null) targetPosition.y = visualizer.SeabedHeightAt(targetPosition);
            _wreck.transform.position = targetPosition;
            _wreck.SetActive(true);

            if (searchStatus.FoundEvent == null)
            {
                IsFound        = false;
                FoundByAgentId = null;
                _foundRing.SetActive(false);
                return;
            }

            if (!IsFound)
            {
                IsFound                = true;
                FoundByAgentId         = searchStatus.FoundEvent.AgentId;
                FoundAtElapsedSeconds  = searchStatus.FoundEvent.ElapsedSimS;
                _blinkStartTime        = Time.time;
                _isBlinking            = true;
                _foundRing.SetActive(true);
            }
        }

        /// <summary>
        /// True during the "flash" half of a blink that started at <paramref name="startTime"/>,
        /// false otherwise and once the blink is over. Same rhythm for the ring and the finder drone.
        /// </summary>
        public static bool IsInBlinkFlash(float startTime)
        {
            float elapsed = Time.time - startTime;
            return elapsed <= BlinkDuration && Mathf.Sin(elapsed * BlinkFrequency) <= 0;
        }

        // About the size of the ship model (36 m long, 8 m wide), +Z = bow, sitting on y = 0.
        private static GameObject BuildSimpleWreck(Material material)
        {
            var simpleWreck = new GameObject("Simple");
            CreatePart(PrimitiveType.Cube, material, simpleWreck.transform,
                new Vector3(0f, 1.8f, -2f), new Vector3(7.5f, 4f, 30f));             // hull
            var bow = CreatePart(PrimitiveType.Cube, material, simpleWreck.transform,
                new Vector3(0f, 1.8f, 14f), new Vector3(5.3f, 4f, 5.3f));            // bow, turned 45°
            bow.transform.localRotation = Quaternion.Euler(0f, 45f, 0f);
            CreatePart(PrimitiveType.Cylinder, material, simpleWreck.transform,
                new Vector3(0f, 7f, 0.5f), new Vector3(0.7f, 3.5f, 0.7f));           // broken mast
            return simpleWreck;
        }
        private static GameObject CreatePart(PrimitiveType primitive, Material material, Transform parent,
            Vector3 localPosition, Vector3 localScale)
        {
            var part = GameObject.CreatePrimitive(primitive);
            Destroy(part.GetComponent<Collider>());
            part.transform.SetParent(parent, false);
            part.transform.localPosition = localPosition;
            part.transform.localScale = localScale;
            part.GetComponent<Renderer>().sharedMaterial = material;
            return part;
        }

        private void OnDestroy()
        {
            DetailedView.Changed -= ApplyView;
            if (_wreck != null) Destroy(_wreck);
            Destroy(_foundRingMaterial);
            Destroy(_simpleWreckMaterial);
            if (_woodMaterial != null) Destroy(_woodMaterial);
        }
    }
}
