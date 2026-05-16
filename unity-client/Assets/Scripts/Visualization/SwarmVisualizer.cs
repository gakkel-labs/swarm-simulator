using System.Collections.Generic;
using Gakkel.Swarm.Contracts.V1;
using UnityEngine;

namespace Gakkel.Swarm.Unity
{
    public class SwarmVisualizer : MonoBehaviour
    {
        [SerializeField] private float floorDepthOffset = 5f;
        [SerializeField] private float groupingRadius = 15f;

        private static readonly Color[] GroupColors =
        {
            new(0.2f, 0.6f, 1.0f),  // bleu
            new(0.2f, 0.8f, 0.2f),  // vert
            new(1.0f, 0.6f, 0.2f),  // orange
            new(0.8f, 0.2f, 0.8f),  // violet
            new(1.0f, 0.9f, 0.2f),  // jaune
            new(0.2f, 0.9f, 0.8f),  // cyan
        };

        private readonly Dictionary<string, GameObject> _agents = new();
        private readonly Dictionary<string, Material> _agentMaterials = new();
        private readonly List<GameObject> _obstacles = new();

        private Material _isolatedMaterial;
        private Material _obstacleMaterial;
        private Material[] _groupMaterials;

        private Vector3 _centroid;
        private GameObject _floor;

        private void Awake()
        {
            var shader = Shader.Find("Universal Render Pipeline/Lit");

            _isolatedMaterial = new Material(shader) { color = Color.gray };
            _obstacleMaterial = new Material(shader) { color = new Color(0.6f, 0.3f, 0.1f) };

            _groupMaterials = new Material[GroupColors.Length];
            for (int i = 0; i < GroupColors.Length; i++)
                _groupMaterials[i] = new Material(shader) { color = GroupColors[i] };

            SpawnMothership(shader);
            SpawnFloor(shader);
        }

        public void Apply(WorldState ws)
        {
            SyncAgents(ws.Agents);
            if (_obstacles.Count == 0)
                SpawnObstacles(ws.Obstacles);
            UpdateCentroid();
            ColorByGroup(ws.Agents);
        }

        public Vector3 GetCentroid() => _centroid;

        // NED (North/East/Down) → Unity (right/up/forward)
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
                    go.name = $"Agent_{agent.Id[..8]}";
                    go.GetComponent<Renderer>().material = _isolatedMaterial;
                    go.transform.localScale = new Vector3(0.5f, 0.5f, 0.5f);
                    _agents[agent.Id] = go;
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
            foreach (var id in toRemove) _agents.Remove(id);
        }

        private void ColorByGroup(IList<AgentState> agents)
        {
            var groupIds = ComputeGroupIds(agents);

            // Count members per group to identify isolated agents (group size == 1)
            var groupSizes = new Dictionary<int, int>();
            foreach (var g in groupIds.Values)
            {
                groupSizes.TryGetValue(g, out int count);
                groupSizes[g] = count + 1;
            }

            foreach (var agent in agents)
            {
                if (!_agents.TryGetValue(agent.Id, out var go)) continue;
                int g = groupIds[agent.Id];
                var mat = groupSizes[g] > 1
                    ? _groupMaterials[g % _groupMaterials.Length]
                    : _isolatedMaterial;
                go.GetComponent<Renderer>().material = mat;
            }
        }

        // BFS connected-components : deux agents sont connectés si distance < groupingRadius
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

        private void SpawnObstacles(IList<Obstacle> obstacles)
        {
            foreach (var obs in obstacles)
            {
                var go = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
                go.name = "Obstacle";
                go.GetComponent<Renderer>().material = _obstacleMaterial;
                float diameter = obs.RadiusM * 2f;
                go.transform.localScale = new Vector3(diameter, 2.5f, diameter);
                go.transform.position = NedToUnity(obs.PositionXyz);
                _obstacles.Add(go);
            }
        }

        private void SpawnMothership(Shader shader)
        {
            var go = GameObject.CreatePrimitive(PrimitiveType.Cube);
            go.name = "Mothership";
            go.GetComponent<Renderer>().material = new Material(shader) { color = Color.gray };
            go.transform.localScale = new Vector3(2f, 1f, 2f);
            go.transform.position = Vector3.zero;
        }

        private void SpawnFloor(Shader shader)
        {
            _floor = GameObject.CreatePrimitive(PrimitiveType.Plane);
            _floor.name = "SeaFloor";
            _floor.GetComponent<Renderer>().material =
                new Material(shader) { color = new Color(0.2f, 0.25f, 0.3f) };
            _floor.transform.localScale = new Vector3(20f, 1f, 20f);
        }

        private void UpdateCentroid()
        {
            if (_agents.Count == 0) return;
            var sum = Vector3.zero;
            foreach (var go in _agents.Values) sum += go.transform.position;
            _centroid = sum / _agents.Count;

            _floor.transform.position = new Vector3(
                _centroid.x,
                _centroid.y - floorDepthOffset,
                _centroid.z);
        }

        private void OnDestroy()
        {
            Destroy(_isolatedMaterial);
            Destroy(_obstacleMaterial);
            foreach (var mat in _groupMaterials) Destroy(mat);
        }
    }
}
