using System.Collections.Generic;
using Gakkel.Swarm.Contracts.V1;
using UnityEngine;

namespace Gakkel.Swarm.Unity
{
    public class SwarmVisualizer : MonoBehaviour
    {
        [SerializeField] private float groupingRadius = 15f;
        [SerializeField] private float obstacleHeightM = 5f;

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

        private readonly Dictionary<string, GameObject> _agents = new();
        private readonly Dictionary<string, Renderer> _agentRenderers = new();
        private readonly Dictionary<string, TrailRenderer> _agentTrails = new();
        private readonly List<GameObject> _obstacles = new();

        private Material _isolatedMaterial;
        private Material _obstacleMaterial;
        private Material _mothershipMaterial;
        private Material _floorMaterial;
        private Material _trailMaterial;
        private Material[] _groupMaterials;

        private bool _obstaclesSpawned;
        private Vector3 _centroid;

        private void Awake()
        {
            var shader = Shader.Find("Universal Render Pipeline/Lit");

            _isolatedMaterial   = new Material(shader) { color = Color.gray };
            _obstacleMaterial   = new Material(shader) { color = new Color(0.6f, 0.3f, 0.1f) };
            _mothershipMaterial = new Material(shader) { color = Color.gray };
            _floorMaterial      = new Material(shader) { color = new Color(0.2f, 0.25f, 0.3f) };
            _trailMaterial      = new Material(Shader.Find("Universal Render Pipeline/Particles/Unlit"));

            _groupMaterials = new Material[GroupColors.Length];
            for (int i = 0; i < GroupColors.Length; i++)
                _groupMaterials[i] = new Material(shader) { color = GroupColors[i] };

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
            ColorByGroup(ws.Agents);
        }

        public Vector3 GetCentroid() => _centroid;

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

                    _agents[agent.Id] = go;
                    _agentRenderers[agent.Id] = rend;
                    _agentTrails[agent.Id] = trail;
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
            }
        }

        private void ColorByGroup(IList<AgentState> agents)
        {
            // O(n²) BFS — acceptable up to ~100 agents at 20 Hz.
            var groupIds = ComputeGroupIds(agents);

            var groupSizes = new Dictionary<int, int>();
            foreach (var g in groupIds.Values)
            {
                groupSizes.TryGetValue(g, out int count);
                groupSizes[g] = count + 1;
            }

            foreach (var agent in agents)
            {
                if (!_agentRenderers.TryGetValue(agent.Id, out var rend)) continue;
                int g = groupIds[agent.Id];
                bool inGroup = groupSizes[g] > 1;
                rend.material = inGroup ? _groupMaterials[g % _groupMaterials.Length] : _isolatedMaterial;

                if (_agentTrails.TryGetValue(agent.Id, out var trail))
                {
                    Color trailColor = inGroup ? GroupColors[g % GroupColors.Length] : Color.gray;
                    trail.colorGradient = MakeTrailGradient(trailColor);
                }
            }
        }

        // BFS connected-components: two agents are connected if distance < groupingRadius.
        private Dictionary<string, int> ComputeGroupIds(IList<AgentState> agents)
        {
            var positions = new Dictionary<string, Vector3>();
            foreach (var a in agents) positions[a.Id] = NedToUnity(a.PositionXyz);

            var groupId = new Dictionary<string, int>();
            var queue = new Queue<string>();
            int nextGroup = 0;

            foreach (var agent in agents)
            {
                if (groupId.ContainsKey(agent.Id)) continue;

                int g = nextGroup++;
                groupId[agent.Id] = g;
                queue.Enqueue(agent.Id);

                while (queue.Count > 0)
                {
                    var current = queue.Dequeue();
                    foreach (var other in agents)
                    {
                        if (groupId.ContainsKey(other.Id)) continue;
                        if (Vector3.Distance(positions[current], positions[other.Id]) < groupingRadius)
                        {
                            groupId[other.Id] = g;
                            queue.Enqueue(other.Id);
                        }
                    }
                }
            }

            return groupId;
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
        }
    }
}
