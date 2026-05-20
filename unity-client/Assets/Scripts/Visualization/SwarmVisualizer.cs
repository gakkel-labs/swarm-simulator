using System.Collections.Generic;
using Gakkel.Swarm.Contracts.V1;
using UnityEngine;

namespace Gakkel.Swarm.Unity
{
    public class SwarmVisualizer : MonoBehaviour
    {
        [SerializeField] private float groupingRadius = 15f;
        [SerializeField] private float obstacleHeightM = 5f;
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

        private readonly Dictionary<string, GameObject> _agents = new();
        private readonly Dictionary<string, Renderer> _agentRenderers = new();
        private readonly Dictionary<string, TrailRenderer> _agentTrails = new();
        private readonly Dictionary<string, LineRenderer> _agentVelocityLines = new();
        private readonly Dictionary<int, GameObject> _groupCentroidSpheres = new();
        private readonly List<GameObject> _obstacles = new();
        private readonly Dictionary<string, Vector3> _agentPositions = new();
        private readonly Vector3[] _velocityLinePositions = new Vector3[5];

        private Material _isolatedMaterial;
        private Material _obstacleMaterial;
        private Material _mothershipMaterial;
        private Material _floorMaterial;
        private Material _trailMaterial;
        private Material[] _groupMaterials;
        private Material[] _groupCentroidMaterials;
        private Gradient[] _groupTrailGradients;
        private Gradient _isolatedTrailGradient;

        private bool _obstaclesSpawned;
        private Vector3 _centroid;
        private string _blinkAgentId;
        private float _blinkAgentStartTime;

        private void Awake()
        {
            var shader = Shader.Find("Universal Render Pipeline/Lit");
            var particleShader = Shader.Find("Universal Render Pipeline/Particles/Unlit");

            _isolatedMaterial   = new Material(shader) { color = Color.gray };
            _obstacleMaterial   = new Material(shader) { color = new Color(0.6f, 0.3f, 0.1f) };
            _mothershipMaterial = new Material(shader) { color = Color.gray };
            _floorMaterial      = new Material(shader) { color = new Color(0.2f, 0.25f, 0.3f) };
            _trailMaterial      = new Material(particleShader);

            _groupMaterials = new Material[GroupColors.Length];
            _groupCentroidMaterials = new Material[GroupColors.Length];
            _groupTrailGradients = new Gradient[GroupColors.Length];
            for (int i = 0; i < GroupColors.Length; i++)
            {
                _groupMaterials[i] = new Material(shader) { color = GroupColors[i] };
                _groupCentroidMaterials[i] = new Material(particleShader) { color = GroupColors[i] };
                _groupTrailGradients[i] = MakeTrailGradient(GroupColors[i]);
            }
            _isolatedTrailGradient = MakeTrailGradient(Color.gray);

            SpawnMothership();
            SpawnFloor();
        }

        public void Apply(WorldState ws)
        {
            SyncAgents(ws.Agents);
            if (!_obstaclesSpawned && ws.Obstacles.Count > 0)
            {
                SpawnObstacles(ws.Obstacles);
                _obstaclesSpawned = true;
            }
            UpdateCentroid();

            _agentPositions.Clear();
            foreach (var a in ws.Agents) _agentPositions[a.Id] = NedToUnity(a.PositionXyz);
            var groupIds = GroupDetector.Compute(_agentPositions, groupingRadius);
            var groupSizes = ComputeGroupSizes(groupIds);

            ColorByGroup(ws.Agents, groupIds, groupSizes);
            UpdateGroupCentroids(_agentPositions, groupIds, groupSizes);
            UpdateVelocityVectors(ws.Agents);
            predatorRenderer?.Apply(ws.Predators);

            if (ws.SearchStatus != null && ws.SearchStatus.FoundEvent != null && _blinkAgentId == null)
            {
                _blinkAgentId        = ws.SearchStatus.FoundEvent.AgentId;
                _blinkAgentStartTime = Time.time;
            }
        }

        public Vector3 GetCentroid() => _centroid;
        public int AgentCount => _agents.Count;
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
            foreach (var line in _agentVelocityLines.Values)
                line.enabled = value;
        }

        public void SetShowCentroids(bool value)
        {
            showCentroids = value;
            foreach (var sphere in _groupCentroidSpheres.Values)
                sphere.SetActive(value);
        }

        private static Dictionary<int, int> ComputeGroupSizes(Dictionary<string, int> groupIds)
        {
            var sizes = new Dictionary<int, int>();
            foreach (var g in groupIds.Values)
            {
                sizes.TryGetValue(g, out int count);
                sizes[g] = count + 1;
            }
            return sizes;
        }

        private void ColorByGroup(IList<AgentState> agents, Dictionary<string, int> groupIds, Dictionary<int, int> groupSizes)
        {
            foreach (var agent in agents)
            {
                if (!_agentRenderers.TryGetValue(agent.Id, out var agentRenderer)) continue;
                int groupId = groupIds[agent.Id];
                bool inGroup = groupSizes[groupId] > 1;
                Color baseColor = inGroup ? GroupColors[groupId % GroupColors.Length] : Color.gray;

                if (agent.Id == _blinkAgentId)
                    agentRenderer.material.color = TargetRenderer.ComputeBlinkColor(baseColor, _blinkAgentStartTime);
                else
                    agentRenderer.material = inGroup ? _groupMaterials[groupId % _groupMaterials.Length] : _isolatedMaterial;

                if (_agentTrails.TryGetValue(agent.Id, out var trail))
                    trail.colorGradient = inGroup ? _groupTrailGradients[groupId % _groupTrailGradients.Length] : _isolatedTrailGradient;
            }
        }

        private void UpdateGroupCentroids(Dictionary<string, Vector3> positions, Dictionary<string, int> groupIds, Dictionary<int, int> groupSizes)
        {
            var groupPositionSums = new Dictionary<int, Vector3>();
            foreach (var (id, pos) in positions)
            {
                int g = groupIds[id];
                if (groupSizes[g] <= 1) continue;
                groupPositionSums.TryGetValue(g, out var sum);
                groupPositionSums[g] = sum + pos;
            }

            var activeGroups = new HashSet<int>();
            foreach (var (g, sum) in groupPositionSums)
            {
                activeGroups.Add(g);
                var centroidPos = sum / groupSizes[g];

                if (!_groupCentroidSpheres.TryGetValue(g, out var marker))
                {
                    marker = CreateCentroidCross(g);
                    _groupCentroidSpheres[g] = marker;
                }

                marker.transform.position = centroidPos;
                marker.SetActive(showCentroids);
            }

            var toRemove = new List<int>();
            foreach (var g in _groupCentroidSpheres.Keys)
                if (!activeGroups.Contains(g)) toRemove.Add(g);
            foreach (var g in toRemove)
            {
                Destroy(_groupCentroidSpheres[g]);
                _groupCentroidSpheres.Remove(g);
            }
        }

        private GameObject CreateCentroidCross(int g)
        {
            var root = new GameObject($"Centroid_G{g}");
            var mat = _groupCentroidMaterials[g % _groupCentroidMaterials.Length];
            float armHalfLength = 1f;
            AddCrossArm(root, mat, -Vector3.right   * armHalfLength, Vector3.right   * armHalfLength, "Arm_X");
            AddCrossArm(root, mat, -Vector3.up      * armHalfLength, Vector3.up      * armHalfLength, "Arm_Y");
            AddCrossArm(root, mat, -Vector3.forward * armHalfLength, Vector3.forward * armHalfLength, "Arm_Z");
            return root;
        }

        private static void AddCrossArm(GameObject parent, Material mat, Vector3 from, Vector3 to, string armName)
        {
            var child = new GameObject(armName);
            child.transform.SetParent(parent.transform, false);
            var lr = child.AddComponent<LineRenderer>();
            lr.positionCount = 2;
            lr.SetPosition(0, from);
            lr.SetPosition(1, to);
            lr.startWidth = lr.endWidth = 0.2f;
            lr.material = mat;
            lr.useWorldSpace = false;
            lr.shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
        }

        private void UpdateVelocityVectors(IList<AgentState> agents)
        {
            foreach (var agent in agents)
            {
                if (!_agentVelocityLines.TryGetValue(agent.Id, out var line)) continue;
                var origin = NedToUnity(agent.PositionXyz);
                var tip = origin + NedToUnity(agent.VelocityMps) * velocityVectorScale;
                var dir = tip - origin;
                if (dir.sqrMagnitude < 1e-6f)
                {
                    for (int i = 0; i < 5; i++) _velocityLinePositions[i] = origin;
                }
                else
                {
                    dir.Normalize();
                    var perp = Vector3.Cross(dir, Vector3.up);
                    if (perp.sqrMagnitude < 0.01f) perp = Vector3.Cross(dir, Vector3.right);
                    perp = perp.normalized * 0.15f;
                    var headBase = tip - dir * 0.3f;
                    _velocityLinePositions[0] = origin;
                    _velocityLinePositions[1] = tip;
                    _velocityLinePositions[2] = headBase + perp;
                    _velocityLinePositions[3] = tip;
                    _velocityLinePositions[4] = headBase - perp;
                }
                line.SetPositions(_velocityLinePositions);
            }
        }

        /// <summary>Converts NED (North/East/Down) to Unity (East/Up/North) coordinates.</summary>
        private static Vector3 NedToUnity(Vec3 ned) => new(ned.Y, -ned.Z, ned.X);

        private void SyncAgents(IList<AgentState> incoming)
        {
            var activeIds = new HashSet<string>();

            foreach (var agent in incoming)
            {
                activeIds.Add(agent.Id);

                if (!_agents.TryGetValue(agent.Id, out var go))
                {
                    go = GameObject.CreatePrimitive(PrimitiveType.Capsule);
                    Destroy(go.GetComponent<Collider>());
                    var shortId = agent.Id.Length >= 8 ? agent.Id[..8] : agent.Id;
                    go.name = $"Agent_{shortId}";
                    var rend = go.GetComponent<Renderer>();
                    rend.material = _isolatedMaterial;
                    go.transform.localScale = new Vector3(0.5f, 0.5f, 0.5f);

                    var trail = go.AddComponent<TrailRenderer>();
                    trail.time = trailTime;
                    trail.startWidth = trailStartWidth;
                    trail.endWidth = 0f;
                    trail.material = _trailMaterial;
                    trail.colorGradient = MakeTrailGradient(Color.gray);
                    trail.shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
                    trail.enabled = showTrails;

                    var line = go.AddComponent<LineRenderer>();
                    line.positionCount = 5;
                    line.startWidth = 0.1f;
                    line.endWidth = 0.05f;
                    line.material = _trailMaterial;
                    line.useWorldSpace = true;
                    line.enabled = showVelocityVectors;

                    _agents[agent.Id] = go;
                    _agentRenderers[agent.Id] = rend;
                    _agentTrails[agent.Id] = trail;
                    _agentVelocityLines[agent.Id] = line;
                }

                go.transform.position = NedToUnity(agent.PositionXyz);
            }

            var toRemove = new List<string>();
            foreach (var (id, go) in _agents)
            {
                if (!activeIds.Contains(id))
                {
                    Destroy(go);
                    toRemove.Add(id);
                }
            }
            foreach (var id in toRemove)
            {
                _agents.Remove(id);
                _agentRenderers.Remove(id);
                _agentTrails.Remove(id);
                _agentVelocityLines.Remove(id);
            }
        }

        // Gradient: head (0) = full opacity, tail (1) = transparent.
        private static Gradient MakeTrailGradient(Color color)
        {
            var g = new Gradient();
            g.SetKeys(
                new[] { new GradientColorKey(color, 0f), new GradientColorKey(color, 1f) },
                new[] { new GradientAlphaKey(0.8f, 0f), new GradientAlphaKey(0f, 1f) }
            );
            return g;
        }

        private void SpawnObstacles(IList<Obstacle> obstacles)
        {
            foreach (var obs in obstacles)
            {
                var go = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
                Destroy(go.GetComponent<Collider>());
                go.name = "Obstacle";
                go.GetComponent<Renderer>().material = _obstacleMaterial;
                float diameter = obs.RadiusM * 2f;
                go.transform.localScale = new Vector3(diameter, obstacleHeightM * 0.5f, diameter);
                go.transform.position = NedToUnity(obs.PositionXyz);
                _obstacles.Add(go);
            }
        }

        private void SpawnMothership()
        {
            var go = GameObject.CreatePrimitive(PrimitiveType.Cube);
            Destroy(go.GetComponent<Collider>());
            go.name = "Mothership";
            go.GetComponent<Renderer>().material = _mothershipMaterial;
            go.transform.localScale = new Vector3(2f, 1f, 2f);
            go.transform.position = Vector3.zero;
        }

        private void SpawnFloor()
        {
            // World is 100×50 server units (X×Z); Plane default is 10×10, so scale (10,1,5).
            // Sea floor is at server Y=0 → NED.Down=100 → Unity Y=-100 (100m below surface).
            // Water surface is at Unity Y=0 (NED.Down=0, server Y=100).
            var floor = GameObject.CreatePrimitive(PrimitiveType.Plane);
            Destroy(floor.GetComponent<Collider>());
            floor.name = "SeaFloor";
            floor.GetComponent<Renderer>().material = _floorMaterial;
            floor.transform.localScale = new Vector3(10f, 1f, 5f);
            floor.transform.position = new Vector3(50f, -100f, 25f);
        }

        private void UpdateCentroid()
        {
            if (_agents.Count == 0) return;
            var sum = Vector3.zero;
            foreach (var go in _agents.Values) sum += go.transform.position;
            _centroid = sum / _agents.Count;
        }

        private void OnDestroy()
        {
            Destroy(_isolatedMaterial);
            Destroy(_obstacleMaterial);
            Destroy(_mothershipMaterial);
            Destroy(_floorMaterial);
            Destroy(_trailMaterial);
            foreach (var mat in _groupMaterials) Destroy(mat);
            foreach (var mat in _groupCentroidMaterials) Destroy(mat);
        }
    }
}
