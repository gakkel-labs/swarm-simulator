using System.Collections.Generic;
using Gakkel.Swarm.Contracts.V1;
using UnityEngine;

namespace Gakkel.Swarm.Unity
{
    public class SwarmVisualizer : MonoBehaviour
    {
        [SerializeField] private float groupingRadius = 15f;
        [SerializeField] private float obstacleHeightMetres = 5f;
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

        private readonly Dictionary<string, GameObject> _agents = new();
        private readonly Dictionary<string, Renderer> _agentRenderers = new();
        private readonly Dictionary<string, TrailRenderer> _agentTrails = new();
        private readonly Dictionary<string, LineRenderer> _agentVelocityLines = new();
        private readonly Dictionary<string, GameObject> _agentDetectionSpheres = new();
        private readonly Dictionary<string, Renderer> _agentDetectionRenderers = new();
        private readonly Dictionary<int, GameObject> _groupCentroidSpheres = new();
        private readonly List<GameObject> _obstacles = new();
        private readonly Dictionary<string, Vector3> _agentPositions = new();
        private readonly Vector3[] _velocityLinePositions = new Vector3[VelocityArrowPointCount];

        private Material _isolatedMaterial;
        private Material _obstacleMaterial;
        private Material _mothershipMaterial;
        private Material _floorMaterial;
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

        private void Awake()
        {
            var shader = Shader.Find("Universal Render Pipeline/Lit");
            var particleShader = Shader.Find("Universal Render Pipeline/Particles/Unlit");

            _isolatedMaterial      = new Material(shader) { color = Color.gray };
            _obstacleMaterial      = new Material(shader) { color = new Color(0.6f, 0.3f, 0.1f) };
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
            SpawnFloor();
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

        public void Apply(WorldState worldState)
        {
            if (worldState.SensorRadiusM > 0f) _sensorRadiusMetres = worldState.SensorRadiusM;
            SyncAgents(worldState.Agents);
            if (!_obstaclesSpawned && worldState.Obstacles.Count > 0)
            {
                SpawnObstacles(worldState.Obstacles);
                _obstaclesSpawned = true;
            }
            UpdateCentroid();

            _agentPositions.Clear();
            foreach (var agentState in worldState.Agents)
                _agentPositions[agentState.Id] = CoordinateUtils.NedToUnity(agentState.PositionXyz);
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
        public int AgentCount    => _agents.Count;
        public int ObstacleCount => _obstacles.Count;

        public void SetShowTrails(bool value)
        {
            showTrails = value;
            foreach (var trail in _agentTrails.Values)
                trail.enabled = value;
        }

        public void SetShowVelocityVectors(bool value)
        {
            showVelocityVectors = value;
            foreach (var velocityLine in _agentVelocityLines.Values)
                velocityLine.enabled = value;
        }

        public void SetShowCentroids(bool value)
        {
            showCentroids = value;
            foreach (var centroidSphere in _groupCentroidSpheres.Values)
                centroidSphere.SetActive(value);
        }

        public void SetShowDetectionZones(bool value)
        {
            showDetectionZones = value;
            foreach (var detectionSphere in _agentDetectionSpheres.Values)
                detectionSphere.SetActive(value);
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
            float armHalfLength = 1f;
            AddCrossArm(root, centroidMaterial, -Vector3.right   * armHalfLength, Vector3.right   * armHalfLength, "Arm_X");
            AddCrossArm(root, centroidMaterial, -Vector3.up      * armHalfLength, Vector3.up      * armHalfLength, "Arm_Y");
            AddCrossArm(root, centroidMaterial, -Vector3.forward * armHalfLength, Vector3.forward * armHalfLength, "Arm_Z");
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
            lineRenderer.startWidth = lineRenderer.endWidth = 0.2f;
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
                if (direction.sqrMagnitude < 1e-6f)
                {
                    for (int i = 0; i < VelocityArrowPointCount; i++) _velocityLinePositions[i] = origin;
                }
                else
                {
                    direction.Normalize();
                    var perpendicular = Vector3.Cross(direction, Vector3.up);
                    if (perpendicular.sqrMagnitude < 0.01f) perpendicular = Vector3.Cross(direction, Vector3.right);
                    perpendicular = perpendicular.normalized * 0.15f;
                    var headBase = tip - direction * 0.3f;
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
                    agentObject = GameObject.CreatePrimitive(PrimitiveType.Capsule);
                    Destroy(agentObject.GetComponent<Collider>());
                    var displayId = agent.Id.Length >= 8 ? agent.Id[..8] : agent.Id;
                    agentObject.name = $"Agent_{displayId}";
                    var agentRenderer = agentObject.GetComponent<Renderer>();
                    agentRenderer.material = _isolatedMaterial;
                    agentObject.transform.localScale = new Vector3(0.5f, 0.5f, 0.5f);

                    var trail = agentObject.AddComponent<TrailRenderer>();
                    trail.time        = trailTime;
                    trail.startWidth  = trailStartWidth;
                    trail.endWidth    = 0f;
                    trail.material    = _trailMaterial;
                    trail.colorGradient       = MakeTrailGradient(Color.gray);
                    trail.shadowCastingMode   = UnityEngine.Rendering.ShadowCastingMode.Off;
                    trail.enabled     = showTrails;

                    var velocityLine = agentObject.AddComponent<LineRenderer>();
                    velocityLine.positionCount      = VelocityArrowPointCount;
                    velocityLine.startWidth         = 0.1f;
                    velocityLine.endWidth           = 0.05f;
                    velocityLine.material           = _trailMaterial;
                    velocityLine.useWorldSpace      = true;
                    velocityLine.enabled            = showVelocityVectors;

                    var detectionSphere = GameObject.CreatePrimitive(PrimitiveType.Sphere);
                    Destroy(detectionSphere.GetComponent<Collider>());
                    detectionSphere.name = $"Detection_{displayId}";
                    var detectionRenderer = detectionSphere.GetComponent<Renderer>();
                    detectionRenderer.sharedMaterial    = _isolatedDetectionMaterial;
                    detectionRenderer.shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
                    float detectionDiameter = _sensorRadiusMetres * 2f;
                    detectionSphere.transform.localScale = new Vector3(detectionDiameter, detectionDiameter, detectionDiameter);
                    detectionSphere.SetActive(showDetectionZones);

                    _agents[agent.Id]                  = agentObject;
                    _agentRenderers[agent.Id]           = agentRenderer;
                    _agentTrails[agent.Id]              = trail;
                    _agentVelocityLines[agent.Id]       = velocityLine;
                    _agentDetectionSpheres[agent.Id]    = detectionSphere;
                    _agentDetectionRenderers[agent.Id]  = detectionRenderer;
                }

                var unityPosition = CoordinateUtils.NedToUnity(agent.PositionXyz);
                agentObject.transform.position = unityPosition;
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
                _agentTrails.Remove(agentId);
                _agentVelocityLines.Remove(agentId);
                _agentDetectionSpheres.Remove(agentId);
                _agentDetectionRenderers.Remove(agentId);
            }
        }

        // Gradient: head (0) = full opacity, tail (1) = transparent.
        private static Gradient MakeTrailGradient(Color color)
        {
            var gradient = new Gradient();
            gradient.SetKeys(
                new[] { new GradientColorKey(color, 0f), new GradientColorKey(color, 1f) },
                new[] { new GradientAlphaKey(0.8f, 0f), new GradientAlphaKey(0f, 1f) }
            );
            return gradient;
        }

        private void SpawnObstacles(IList<Obstacle> obstacles)
        {
            foreach (var obstacle in obstacles)
            {
                var obstacleObject = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
                Destroy(obstacleObject.GetComponent<Collider>());
                obstacleObject.name = "Obstacle";
                obstacleObject.GetComponent<Renderer>().material = _obstacleMaterial;
                float diameter = obstacle.RadiusM * 2f;
                obstacleObject.transform.localScale = new Vector3(diameter, obstacleHeightMetres * 0.5f, diameter);
                obstacleObject.transform.position = CoordinateUtils.NedToUnity(obstacle.PositionXyz);
                _obstacles.Add(obstacleObject);
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
            floorObject.transform.position = new Vector3(50f, -100f, 25f);
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
            Destroy(_isolatedMaterial);
            Destroy(_obstacleMaterial);
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
