using System.Collections.Generic;
using Gakkel.Swarm.Contracts.V1;
using UnityEngine;

namespace Gakkel.Swarm.Unity
{
    public class PredatorRenderer : MonoBehaviour
    {
        private readonly Dictionary<string, GameObject> _predators = new();
        private Material _predatorMaterial;

        private void Awake()
        {
            var shader = Shader.Find("Universal Render Pipeline/Lit");
            _predatorMaterial = new Material(shader) { color = Color.red };
        }

        public void Apply(IList<PredatorState> predators)
        {
            var activeIds = new HashSet<string>();

            foreach (var p in predators)
            {
                activeIds.Add(p.Id);

                if (!_predators.TryGetValue(p.Id, out var go))
                {
                    go = GameObject.CreatePrimitive(PrimitiveType.Sphere);
                    Destroy(go.GetComponent<Collider>());
                    go.name = "Predator";
                    go.GetComponent<Renderer>().material = _predatorMaterial;
                    go.transform.localScale = Vector3.one * 2f;
                    _predators[p.Id] = go;
                }

                go.transform.position = NedToUnity(p.PositionXyz);
            }

            var toRemove = new List<string>();
            foreach (var (id, go) in _predators)
            {
                if (!activeIds.Contains(id))
                {
                    Destroy(go);
                    toRemove.Add(id);
                }
            }
            foreach (var id in toRemove) _predators.Remove(id);
        }

        private static Vector3 NedToUnity(Vec3 ned) => new(ned.Y, -ned.Z, ned.X);

        private void OnDestroy()
        {
            Destroy(_predatorMaterial);
        }
    }
}
