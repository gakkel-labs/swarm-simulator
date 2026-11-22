using System.Collections.Generic;
using Gakkel.Swarm.Contracts.V1;
using UnityEngine;
using UnityEngine.InputSystem;

namespace Gakkel.Swarm.Unity
{
    public class SwarmVisualizer : MonoBehaviour
    {
        [SerializeField] private float groupingRadius = 15f;
        [SerializeField] private PredatorRenderer predatorRenderer;

        private static readonly Color[] GroupColors =
        {
            new(0.2f, 0.6f, 1.0f),  // blue
            new(0.2f, 0.8f, 0.2f),  // green
            new(1.0f, 0.6f, 0.2f),  // orange
            new(0.8f, 0.2f, 0.8f),  // purple
            new(1.0f, 0.9f, 0.2f),  // yellow
            new(0.2f, 0.9f, 0.8f),  // cyan
        };

        [SerializeField] private float trailTime = 2f;
        [SerializeField] private float trailStartWidth = 0.15f;
        [SerializeField] private bool showTrails = true;
        [SerializeField] private bool showVelocityVectors = false;
        [SerializeField] private float velocityVectorScale = 0.67f;
        [SerializeField] private bool showCentroids = false;
        [SerializeField] private bool showDetectionZones = false;

        [Header("Drone look (V key toggles ROV / capsule)")]
        [Tooltip("ROV model to show for each drone (e.g. Assets/Models/GakkelROV.glb). Front must be +Z. Empty = capsules only.")]
        [SerializeField] private GameObject rovModel;
        [SerializeField] private float rovScale = 2f;
        [SerializeField] private bool showRovModel = true;

        [Header("Seabed (IBCAO / GEBCO bathymetry, see docs/04-seabed-bathymetry.md)")]
        [Tooltip("Real relief of the Gakkel Ridge. Off = old flat floor.")]
        [SerializeField] private bool useBathymetrySeabed = true;
        [Tooltip("Vertical scale of the real relief. 1 = real relief (a 100 m grid is still smoother than the real seabed). " +
                 "Can be changed in Play Mode.")]
        [SerializeField, Range(1f, 10f)] private float seabedVerticalExaggeration = 3f;
        [Tooltip("Sediment, basalt and hydrothermal textures. Off = plain sand colour.")]
        [SerializeField] private bool useSeabedTextures = true;
        [Tooltip("Basalt boulders and pebbles around the swarm, more on slopes. Off = bare seabed. B key toggles them in Play Mode.")]
        [SerializeField] private bool useSeabedRocks = true;
        [Tooltip("Point of the seabed placed under the swarm, in metres from the terrain centre (X east, Y north). " +
                 "Default: flat ground near a 47 m wall (x3: 140 m), 0.9 km from the Aurora vents (0, 0).")]
        [SerializeField] private Vector2 seabedFocusOffset = new(700f, -600f);

        private readonly Dictionary<string, GameObject> _agents = new();
        private readonly Dictionary<string, Renderer> _agentRenderers = new();
        private readonly Dictionary<string, GameObject> _agentCapsules = new();
        private readonly Dictionary<string, GameObject> _agentRovs = new();
        private readonly Dictionary<string, TrailRenderer> _agentTrails = new();
        private readonly Dictionary<string, LineRenderer> _agentVelocityLines = new();
        private readonly Dictionary<string, GameObject> _agentDetectionSpheres = new();
        private readonly Dictionary<string, Renderer> _agentDetectionRenderers = new();
        private readonly Dictionary<int, GameObject> _groupCentroidSpheres = new();
        private readonly List<GameObject> _obstacles = new();
        private readonly Dictionary<string, Vector3> _agentPositions = new();
        private readonly Dictionary<string, Vector3> _droneVelocities = new();
        private readonly Vector3[] _velocityLinePositions = new Vector3[VelocityArrowPointCount];

        private Material _isolatedMaterial;
        private Material _mothershipMaterial;
        private Material _floorMaterial;
        private SeabedTerrain _seabed;
        private UnderwaterAtmosphere _atmosphere;
        private float _appliedSeabedExaggeration;
        private Material _trailMaterial;
        private Material _isolatedDetectionMaterial;
        private Material[] _groupMaterials;
        private Material[] _groupCentroidMaterials;
        private Material[] _groupDetectionMaterials;
        private Gradient[] _groupTrailGradients;
        private Gradient _isolatedTrailGradient;

        private bool _obstaclesSpawned;
        private Vector3 _centroid;
        private string _blinkAgentId;
        private float _blinkAgentStartTime;
        private float _sensorRadiusMetres = 20f;

        private const float DetectionZoneAlpha = 0.08f;
        private const int VelocityArrowPointCount = 5;
        private const float AgentCapsuleScale = 0.5f;
        private const float VelocityArrowStartWidth = 0.1f;
        private const float VelocityArrowEndWidth = 0.05f;
        private const float VelocityArrowHeadSize = 0.15f;
        private const float VelocityArrowHeadOffsetFromTip = 0.3f;
        private const float VelocityArrowMinDirectionSqrMagnitude = 1e-6f;
        private const float RovMinHeadingSqrMagnitude = 1e-4f;
        private const float VelocityArrowPerpendicularFallbackThreshold = 0.01f;
        private const float CentroidCrossArmHalfLength = 1f;
        private const float CentroidCrossArmWidth = 0.2f;
        private const float TrailHeadAlpha = 0.8f;
        private const float TrailTailAlpha = 0f;
        private static readonly Vector3 FloorCentre = new(50f, -100f, 25f);
        private static readonly Vector2 FloorHalfSize = new(50f, 25f); // swarm footprint: 100 x 50 m (X x Z)

        private void Awake()
        {
            var shader = Shader.Find("Universal Render Pipeline/Lit");
            var particleShader = Shader.Find("Universal Render Pipeline/Particles/Unlit");

            _isolatedMaterial      = new Material(shader) { color = Color.gray };
            _mothershipMaterial    = new Material(shader) { color = Color.gray };
            _floorMaterial         = new Material(shader) { color = new Color(0.2f, 0.25f, 0.3f) };
            _trailMaterial         = new Material(particleShader);
            _isolatedDetectionMaterial = MakeTransparentMaterial(shader, new Color(0.5f, 0.5f, 0.5f, DetectionZoneAlpha));

            _groupMaterials          = new Material[GroupColors.Length];
            _groupCentroidMaterials  = new Material[GroupColors.Length];
            _groupDetectionMaterials = new Material[GroupColors.Length];
            _groupTrailGradients     = new Gradient[GroupColors.Length];
            for (int i = 0; i < GroupColors.Length; i++)
            {
                _groupMaterials[i]         = new Material(shader) { color = GroupColors[i] };
                _groupCentroidMaterials[i] = new Material(particleShader) { color = GroupColors[i] };
                var groupColor = GroupColors[i];
                _groupDetectionMaterials[i] = MakeTransparentMaterial(shader, new Color(groupColor.r, groupColor.g, groupColor.b, DetectionZoneAlpha));
                _groupTrailGradients[i]    = MakeTrailGradient(GroupColors[i]);
            }
            _isolatedTrailGradient = MakeTrailGradient(Color.gray);

            SpawnMothership();
            SpawnSeabed();
            _atmosphere = UnderwaterAtmosphere.Create(this);

            // The Inspector value "Show Rov Model" sets the starting view.
            DetailedView.Changed += OnDetailedViewChanged;
            DetailedView.Set(showRovModel);
        }

        // URP/Lit needs explicit setup to render transparent: surface type, blend modes, ZWrite off,
        // keyword toggle, transparent render queue. _BaseColor is the URP-native property (color falls
        // back to it via name aliasing but we set both for safety).
        private static Material MakeTransparentMaterial(Shader litShader, Color color)
        {
            var material = new Material(litShader);
            material.SetOverrideTag("RenderType", "Transparent");
            material.SetFloat("_Surface", 1f);
            material.SetFloat("_Blend", 0f);
            material.SetFloat("_SrcBlend", (float)UnityEngine.Rendering.BlendMode.SrcAlpha);
            material.SetFloat("_DstBlend", (float)UnityEngine.Rendering.BlendMode.OneMinusSrcAlpha);
            material.SetFloat("_ZWrite", 0f);
            material.EnableKeyword("_SURFACE_TYPE_TRANSPARENT");
            material.DisableKeyword("_ALPHATEST_ON");
            material.DisableKeyword("_ALPHAMODULATE_ON");
            material.renderQueue = (int)UnityEngine.Rendering.RenderQueue.Transparent;
            material.SetColor("_BaseColor", color);
            material.color = color;
            return material;
        }

#if UNITY_EDITOR
        private const string DefaultRovModelPath = "Assets/Models/GakkelROV.glb";

        // Editor only: if nobody has set the ROV model in the Inspector yet, pick the one in the repo.
        // Save the scene (Ctrl+S) once to keep it.
        private void OnValidate()
        {
            if (rovModel == null)
                rovModel = UnityEditor.AssetDatabase.LoadAssetAtPath<GameObject>(DefaultRovModelPath);
        }
#endif

        private void Update()
        {
            var keyboard = Keyboard.current;
            if (keyboard != null && keyboard.vKey.wasPressedThisFrame)
                DetailedView.Set(!DetailedView.IsOn);
            if (keyboard != null && keyboard.bKey.wasPressedThisFrame)
                SetShowSeabedRocks(!useSeabedRocks);

            if (_seabed != null && !Mathf.Approximately(_appliedSeabedExaggeration, seabedVerticalExaggeration))
            {
                _seabed.SetVerticalExaggeration(seabedVerticalExaggeration);
                _appliedSeabedExaggeration = seabedVerticalExaggeration;
            }
        }

        public void Apply(WorldState worldState)
        {
            if (worldState.SensorRadiusM > 0f) _sensorRadiusMetres = worldState.SensorRadiusM;
            CohesionSpreadM = worldState.CohesionSpreadM;
            SyncAgents(worldState.Agents);
            if (!_obstaclesSpawned && worldState.Obstacles.Count > 0)
            {
                SpawnObstacles(worldState.Obstacles);
                _obstaclesSpawned = true;
            }
            UpdateCentroid();

            _agentPositions.Clear();
            _droneVelocities.Clear();
            foreach (var agentState in worldState.Agents)
            {
                _agentPositions[agentState.Id]  = CoordinateUtils.NedToUnity(agentState.PositionXyz);
                _droneVelocities[agentState.Id] = CoordinateUtils.NedToUnity(agentState.VelocityMps);
            }
            var groupIds   = GroupDetector.Compute(_agentPositions, groupingRadius);
            var groupSizes = ComputeGroupSizes(groupIds);

            ColorByGroup(worldState.Agents, groupIds, groupSizes);
            UpdateGroupCentroids(_agentPositions, groupIds, groupSizes);
            UpdateVelocityVectors(worldState.Agents);
            predatorRenderer?.Apply(worldState.Predators);

            if (worldState.SearchStatus != null)
            {
                if (worldState.SearchStatus.FoundEvent == null)
                    _blinkAgentId = null;
                else if (_blinkAgentId == null)
                {
                    _blinkAgentId        = worldState.SearchStatus.FoundEvent.AgentId;
                    _blinkAgentStartTime = Time.time;
                }
            }
        }

        public Vector3 GetCentroid() => _centroid;

        // Ids of the drones shown right now (used by the follow camera to pick a drone).
        public IEnumerable<string> DroneIds => _agentPositions.Keys;

        // Position and velocity of one drone, in Unity coordinates. False if the drone is gone.
        public bool TryGetDroneMotion(string droneId, out Vector3 position, out Vector3 velocity)
        {
            velocity = Vector3.zero;
            return _agentPositions.TryGetValue(droneId, out position)
                && _droneVelocities.TryGetValue(droneId, out velocity);
        }

        public int AgentCount      => _agents.Count;
        public int ObstacleCount   => _obstacles.Count;
        public float CohesionSpreadM { get; private set; }
        public bool HasRovModel       => rovModel != null;
        public bool IsShowingRovModel => showRovModel && rovModel != null;

        public bool HasSeabed            => _seabed != null;
        public bool IsShowingSeabedRocks => _seabed != null && _seabed.HasVisibleRocks;

        // B key: lighter scene without rocks, or the full one. The simple view (V) always hides them.
        public void SetShowSeabedRocks(bool shouldShow)
        {
            useSeabedRocks = shouldShow;
            if (_seabed != null) _seabed.SetRocksVisible(shouldShow && DetailedView.IsOn);
        }

        /// <summary>World height of the seabed (or of the flat floor) at a world X/Z.</summary>
        public float SeabedHeightAt(Vector3 worldPosition)
            => _seabed != null ? _seabed.GroundHeightAt(worldPosition) : FloorCentre.y;

        // UI check box: underwater fog, light, caustics, marine snow and post-processing, or the plain scene.
        public void SetShowUnderwaterAtmosphere(bool shouldShow)
        {
            if (_atmosphere != null) _atmosphere.enabled = shouldShow;
        }

        // V key: detailed or simple view for the whole scene (see DetailedView).
        public void SetShowRovModel(bool shouldShow) => DetailedView.Set(shouldShow);

        private void OnDetailedViewChanged(bool isDetailed)
        {
            showRovModel = isDetailed;
            if (_seabed != null) _seabed.SetRocksVisible(isDetailed && useSeabedRocks);
            bool rovVisible = IsShowingRovModel;
            foreach (var capsule in _agentCapsules.Values)
                capsule.SetActive(!rovVisible);
            foreach (var rov in _agentRovs.Values)
                rov.SetActive(rovVisible);
        }

        public void SetShowTrails(bool shouldShow)
        {
            showTrails = shouldShow;
            foreach (var trail in _agentTrails.Values)
                trail.enabled = shouldShow;
        }

        public void SetShowVelocityVectors(bool shouldShow)
        {
            showVelocityVectors = shouldShow;
            foreach (var velocityLine in _agentVelocityLines.Values)
                velocityLine.enabled = shouldShow;
        }

        public void SetShowCentroids(bool shouldShow)
        {
            showCentroids = shouldShow;
            foreach (var centroidSphere in _groupCentroidSpheres.Values)
                centroidSphere.SetActive(shouldShow);
        }

        public void SetShowDetectionZones(bool shouldShow)
        {
            showDetectionZones = shouldShow;
            foreach (var detectionSphere in _agentDetectionSpheres.Values)
                detectionSphere.SetActive(shouldShow);
        }

        private static Dictionary<int, int> ComputeGroupSizes(Dictionary<string, int> groupIds)
        {
            var groupSizes = new Dictionary<int, int>();
            foreach (var groupId in groupIds.Values)
            {
                groupSizes.TryGetValue(groupId, out int count);
                groupSizes[groupId] = count + 1;
            }
            return groupSizes;
        }

        private void ColorByGroup(IList<AgentState> agents, Dictionary<string, int> groupIds, Dictionary<int, int> groupSizes)
        {
            foreach (var agent in agents)
            {
                if (!_agentRenderers.TryGetValue(agent.Id, out var agentRenderer)) continue;
                int groupId  = groupIds[agent.Id];
                bool inGroup = groupSizes[groupId] > 1;
                Color baseColor = inGroup ? GroupColors[groupId % GroupColors.Length] : Color.gray;

                if (agent.Id == _blinkAgentId)
                    agentRenderer.material.color = TargetRenderer.ComputeBlinkColor(baseColor, _blinkAgentStartTime);
                else
                    agentRenderer.material = inGroup ? _groupMaterials[groupId % _groupMaterials.Length] : _isolatedMaterial;

                if (_agentTrails.TryGetValue(agent.Id, out var trail))
                    trail.colorGradient = inGroup ? _groupTrailGradients[groupId % _groupTrailGradients.Length] : _isolatedTrailGradient;

                if (_agentDetectionRenderers.TryGetValue(agent.Id, out var detectionRenderer))
                {
                    detectionRenderer.sharedMaterial = inGroup
                        ? _groupDetectionMaterials[groupId % _groupDetectionMaterials.Length]
                        : _isolatedDetectionMaterial;
                }
            }
        }

        private void UpdateGroupCentroids(Dictionary<string, Vector3> positions, Dictionary<string, int> groupIds, Dictionary<int, int> groupSizes)
        {
            var groupPositionSums = new Dictionary<int, Vector3>();
            foreach (var (agentId, agentPosition) in positions)
            {
                int groupId = groupIds[agentId];
                if (groupSizes[groupId] <= 1) continue;
                groupPositionSums.TryGetValue(groupId, out var positionSum);
                groupPositionSums[groupId] = positionSum + agentPosition;
            }

            var activeGroups = new HashSet<int>();
            foreach (var (groupId, positionSum) in groupPositionSums)
            {
                activeGroups.Add(groupId);
                var centroidPosition = positionSum / groupSizes[groupId];

                if (!_groupCentroidSpheres.TryGetValue(groupId, out var marker))
                {
                    marker = CreateCentroidCross(groupId);
                    _groupCentroidSpheres[groupId] = marker;
                }

                marker.transform.position = centroidPosition;
                marker.SetActive(showCentroids);
            }

            var groupsToRemove = new List<int>();
            foreach (var groupId in _groupCentroidSpheres.Keys)
                if (!activeGroups.Contains(groupId)) groupsToRemove.Add(groupId);
            foreach (var groupId in groupsToRemove)
            {
                Destroy(_groupCentroidSpheres[groupId]);
                _groupCentroidSpheres.Remove(groupId);
            }
        }

        private GameObject CreateCentroidCross(int groupIndex)
        {
            var root = new GameObject($"Centroid_G{groupIndex}");
            var centroidMaterial = _groupCentroidMaterials[groupIndex % _groupCentroidMaterials.Length];
            AddCrossArm(root, centroidMaterial, -Vector3.right   * CentroidCrossArmHalfLength, Vector3.right   * CentroidCrossArmHalfLength, "Arm_X");
            AddCrossArm(root, centroidMaterial, -Vector3.up      * CentroidCrossArmHalfLength, Vector3.up      * CentroidCrossArmHalfLength, "Arm_Y");
            AddCrossArm(root, centroidMaterial, -Vector3.forward * CentroidCrossArmHalfLength, Vector3.forward * CentroidCrossArmHalfLength, "Arm_Z");
            return root;
        }

        private static void AddCrossArm(GameObject parent, Material material, Vector3 from, Vector3 to, string armName)
        {
            var child = new GameObject(armName);
            child.transform.SetParent(parent.transform, false);
            var lineRenderer = child.AddComponent<LineRenderer>();
            lineRenderer.positionCount = 2;
            lineRenderer.SetPosition(0, from);
            lineRenderer.SetPosition(1, to);
            lineRenderer.startWidth = lineRenderer.endWidth = CentroidCrossArmWidth;
            lineRenderer.material = material;
            lineRenderer.useWorldSpace = false;
            lineRenderer.shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
        }

        private void UpdateVelocityVectors(IList<AgentState> agents)
        {
            foreach (var agent in agents)
            {
                if (!_agentVelocityLines.TryGetValue(agent.Id, out var velocityLine)) continue;
                var origin    = CoordinateUtils.NedToUnity(agent.PositionXyz);
                var tip       = origin + CoordinateUtils.NedToUnity(agent.VelocityMps) * velocityVectorScale;
                var direction = tip - origin;
                if (direction.sqrMagnitude < VelocityArrowMinDirectionSqrMagnitude)
                {
                    for (int i = 0; i < VelocityArrowPointCount; i++) _velocityLinePositions[i] = origin;
                }
                else
                {
                    direction.Normalize();
                    var perpendicular = Vector3.Cross(direction, Vector3.up);
                    if (perpendicular.sqrMagnitude < VelocityArrowPerpendicularFallbackThreshold)
                        perpendicular = Vector3.Cross(direction, Vector3.right);
                    perpendicular = perpendicular.normalized * VelocityArrowHeadSize;
                    var headBase = tip - direction * VelocityArrowHeadOffsetFromTip;
                    _velocityLinePositions[0] = origin;
                    _velocityLinePositions[1] = tip;
                    _velocityLinePositions[2] = headBase + perpendicular;
                    _velocityLinePositions[3] = tip;
                    _velocityLinePositions[4] = headBase - perpendicular;
                }
                velocityLine.SetPositions(_velocityLinePositions);
            }
        }

        private void SyncAgents(IList<AgentState> incoming)
        {
            var activeIds = new HashSet<string>();

            foreach (var agent in incoming)
            {
                activeIds.Add(agent.Id);

                if (!_agents.TryGetValue(agent.Id, out var agentObject))
                {
                    var displayId = agent.Id.Length >= 8 ? agent.Id[..8] : agent.Id;
                    agentObject = CreateAgentVisual(displayId, out var agentRenderer, out var capsule, out var rov, out var trail, out var velocityLine);
                    var detectionSphere = CreateDetectionSphere(displayId, out var detectionRenderer);

                    _agents[agent.Id]                  = agentObject;
                    _agentRenderers[agent.Id]          = agentRenderer;
                    _agentCapsules[agent.Id]           = capsule;
                    if (rov != null) _agentRovs[agent.Id] = rov;
                    _agentTrails[agent.Id]             = trail;
                    _agentVelocityLines[agent.Id]      = velocityLine;
                    _agentDetectionSpheres[agent.Id]   = detectionSphere;
                    _agentDetectionRenderers[agent.Id] = detectionRenderer;
                }

                var unityPosition = CoordinateUtils.NedToUnity(agent.PositionXyz);
                agentObject.transform.position = unityPosition;
                if (_agentRovs.TryGetValue(agent.Id, out var rovObject))
                    OrientAlongVelocity(rovObject.transform, agent.VelocityMps);
                if (_agentDetectionSpheres.TryGetValue(agent.Id, out var detectionSphereObject))
                    detectionSphereObject.transform.position = unityPosition;
            }

            var agentsToRemove = new List<string>();
            foreach (var (agentId, agentObject) in _agents)
            {
                if (!activeIds.Contains(agentId))
                {
                    Destroy(agentObject);
                    if (_agentDetectionSpheres.TryGetValue(agentId, out var detectionSphere))
                        Destroy(detectionSphere);
                    agentsToRemove.Add(agentId);
                }
            }
            foreach (var agentId in agentsToRemove)
            {
                _agents.Remove(agentId);
                _agentRenderers.Remove(agentId);
                _agentCapsules.Remove(agentId);
                _agentRovs.Remove(agentId);
                _agentTrails.Remove(agentId);
                _agentVelocityLines.Remove(agentId);
                _agentDetectionSpheres.Remove(agentId);
                _agentDetectionRenderers.Remove(agentId);
            }
        }

        // The ROV model's front is +Z, so LookRotation(velocity) points it where it is going.
        // Below a minimal speed we keep the last heading instead of snapping to a default.
        private static void OrientAlongVelocity(Transform rovTransform, Vec3 velocityNed)
        {
            var heading = CoordinateUtils.NedToUnity(velocityNed);
            if (heading.sqrMagnitude < RovMinHeadingSqrMagnitude) return;
            rovTransform.rotation = Quaternion.LookRotation(heading, Vector3.up);
        }

        // Agent root = empty GameObject that carries the trail and the velocity arrow.
        // Two children hold the two looks: "Capsule" (always) and "ROV" (if a model is set).
        // Only one of them is active at a time, see SetShowRovModel.
        private GameObject CreateAgentVisual(string displayId, out Renderer agentRenderer, out GameObject capsule,
                                             out GameObject rov, out TrailRenderer trail, out LineRenderer velocityLine)
        {
            var agentObject = new GameObject($"Agent_{displayId}");
            bool rovVisible = IsShowingRovModel;

            capsule = GameObject.CreatePrimitive(PrimitiveType.Capsule);
            Destroy(capsule.GetComponent<Collider>());
            capsule.name = "Capsule";
            capsule.transform.SetParent(agentObject.transform, false);
            capsule.transform.localScale = new Vector3(AgentCapsuleScale, AgentCapsuleScale, AgentCapsuleScale);
            agentRenderer = capsule.GetComponent<Renderer>();
            agentRenderer.material = _isolatedMaterial;
            capsule.SetActive(!rovVisible);

            rov = null;
            if (rovModel != null)
            {
                rov = Instantiate(rovModel, agentObject.transform, false);
                rov.name = "ROV";
                rov.transform.localScale = new Vector3(rovScale, rovScale, rovScale);
                foreach (var rovCollider in rov.GetComponentsInChildren<Collider>())
                    Destroy(rovCollider);
                rov.SetActive(rovVisible);
            }

            trail = agentObject.AddComponent<TrailRenderer>();
            trail.time              = trailTime;
            trail.startWidth        = trailStartWidth;
            trail.endWidth          = 0f;
            trail.material          = _trailMaterial;
            trail.colorGradient     = MakeTrailGradient(Color.gray);
            trail.shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
            trail.enabled           = showTrails;

            velocityLine = agentObject.AddComponent<LineRenderer>();
            velocityLine.positionCount = VelocityArrowPointCount;
            velocityLine.startWidth    = VelocityArrowStartWidth;
            velocityLine.endWidth      = VelocityArrowEndWidth;
            velocityLine.material      = _trailMaterial;
            velocityLine.useWorldSpace = true;
            velocityLine.enabled       = showVelocityVectors;

            return agentObject;
        }

        private GameObject CreateDetectionSphere(string displayId, out Renderer detectionRenderer)
        {
            var detectionSphere = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            Destroy(detectionSphere.GetComponent<Collider>());
            detectionSphere.name = $"Detection_{displayId}";
            detectionRenderer = detectionSphere.GetComponent<Renderer>();
            detectionRenderer.sharedMaterial    = _isolatedDetectionMaterial;
            detectionRenderer.shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
            float detectionDiameter = _sensorRadiusMetres * 2f;
            detectionSphere.transform.localScale = new Vector3(detectionDiameter, detectionDiameter, detectionDiameter);
            detectionSphere.SetActive(showDetectionZones);

            return detectionSphere;
        }

        // Gradient: head (0) = full opacity, tail (1) = transparent.
        private static Gradient MakeTrailGradient(Color color)
        {
            var gradient = new Gradient();
            gradient.SetKeys(
                new[] { new GradientColorKey(color, 0f), new GradientColorKey(color, 1f) },
                new[] { new GradientAlphaKey(TrailHeadAlpha, 0f), new GradientAlphaKey(TrailTailAlpha, 1f) }
            );
            return gradient;
        }

        private void SpawnObstacles(IList<Obstacle> obstacles)
        {
            // Each obstacle is drawn as a hydrothermal chimney rising from the seabed up to it.
            // The server still sees a sphere, so drones can cross the chimney below it (issue #142).
            for (var obstacleIndex = 0; obstacleIndex < obstacles.Count; obstacleIndex++)
            {
                var obstacle = obstacles[obstacleIndex];
                var obstaclePosition = CoordinateUtils.NedToUnity(obstacle.PositionXyz);
                var chimney = HydrothermalChimney.Spawn(obstaclePosition, obstacle.RadiusM,
                    SeabedHeightAt(obstaclePosition), seed: obstacleIndex + 1);
                _obstacles.Add(chimney.gameObject);
            }
        }

        private void SpawnMothership()
        {
            var mothershipObject = GameObject.CreatePrimitive(PrimitiveType.Cube);
            Destroy(mothershipObject.GetComponent<Collider>());
            mothershipObject.name = "Mothership";
            mothershipObject.GetComponent<Renderer>().material = _mothershipMaterial;
            mothershipObject.transform.localScale = new Vector3(2f, 1f, 2f);
            mothershipObject.transform.position = Vector3.zero;
        }

        // Falls back to the flat floor if the bathymetry is turned off or its heightmap is missing.
        private void SpawnSeabed()
        {
            if (useBathymetrySeabed)
            {
                _seabed = SeabedTerrain.Spawn(FloorCentre, FloorHalfSize, seabedVerticalExaggeration,
                    seabedFocusOffset, useSeabedTextures, useSeabedRocks && showRovModel);
                _appliedSeabedExaggeration = seabedVerticalExaggeration;
            }
            if (_seabed == null) SpawnFloor();
        }

        private void SpawnFloor()
        {
            // World is 100×50 server units (X×Z); Plane default is 10×10, so scale (10,1,5).
            // Sea floor is at server Y=0 → NED.Down=100 → Unity Y=-100 (100m below surface).
            // Water surface is at Unity Y=0 (NED.Down=0, server Y=100).
            var floorObject = GameObject.CreatePrimitive(PrimitiveType.Plane);
            Destroy(floorObject.GetComponent<Collider>());
            floorObject.name = "SeaFloor";
            floorObject.GetComponent<Renderer>().material = _floorMaterial;
            floorObject.transform.localScale = new Vector3(10f, 1f, 5f);
            floorObject.transform.position = FloorCentre;
        }

        private void UpdateCentroid()
        {
            if (_agents.Count == 0) return;
            var positionSum = Vector3.zero;
            foreach (var agentObject in _agents.Values) positionSum += agentObject.transform.position;
            _centroid = positionSum / _agents.Count;
        }

        private void OnDestroy()
        {
            DetailedView.Changed -= OnDetailedViewChanged;
            Destroy(_isolatedMaterial);
            Destroy(_mothershipMaterial);
            Destroy(_floorMaterial);
            Destroy(_trailMaterial);
            Destroy(_isolatedDetectionMaterial);
            foreach (var material in _groupMaterials) Destroy(material);
            foreach (var material in _groupCentroidMaterials) Destroy(material);
            foreach (var material in _groupDetectionMaterials) Destroy(material);
        }
    }
}
