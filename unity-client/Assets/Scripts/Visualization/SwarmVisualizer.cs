using System.Collections.Generic;
using Gakkel.Swarm.Contracts.V1;
using UnityEngine;

namespace Gakkel.Swarm.Unity
{
    public class SwarmVisualizer : MonoBehaviour
    {
        [SerializeField] private float floorDepthOffset = 5f;

        private readonly Dictionary<string, GameObject> _agents = new();
        private readonly List<GameObject> _obstacles = new();

        private Material _agentMaterial;
        private Material _obstacleMaterial;

        private Vector3 _centroid;
        private GameObject _floor;

        private void Awake()
        {
            var shader = Shader.Find("Universal Render Pipeline/Lit");

            _agentMaterial = new Material(shader) { color = Color.gray };
            _obstacleMaterial = new Material(shader) { color = new Color(0.6f, 0.3f, 0.1f) };

            SpawnMothership(shader);
            SpawnFloor(shader);
        }

        public void Apply(WorldState ws)
        {
            SyncAgents(ws.Agents);
            if (_obstacles.Count == 0)
                SpawnObstacles(ws.Obstacles);
            UpdateCentroid();
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
                    go.GetComponent<Renderer>().material = _agentMaterial;
                    go.transform.localScale = new Vector3(0.5f, 0.5f, 0.5f);
                    _agents[agent.Id] = go;
                }

                go.transform.position = NedToUnity(agent.PositionXyz);
            }

            // Destroy agents no longer in the WorldState
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

            // Floor follows centroid horizontally, sits below the swarm.
            _floor.transform.position = new Vector3(
                _centroid.x,
                _centroid.y - floorDepthOffset,
                _centroid.z);
        }

        private void OnDestroy()
        {
            Destroy(_agentMaterial);
            Destroy(_obstacleMaterial);
        }
    }
}
