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

            foreach (var predatorState in predators)
            {
                activeIds.Add(predatorState.Id);

                if (!_predators.TryGetValue(predatorState.Id, out var predatorObject))
                {
                    predatorObject = GameObject.CreatePrimitive(PrimitiveType.Sphere);
                    Destroy(predatorObject.GetComponent<Collider>());
                    predatorObject.name = "Predator";
                    predatorObject.GetComponent<Renderer>().material = _predatorMaterial;
                    predatorObject.transform.localScale = Vector3.one * 2f;
                    _predators[predatorState.Id] = predatorObject;
                }

                predatorObject.transform.position = CoordinateUtils.NedToUnity(predatorState.PositionXyz);
            }

            var predatorsToRemove = new List<string>();
            foreach (var (predatorId, predatorObject) in _predators)
            {
                if (!activeIds.Contains(predatorId))
                {
                    Destroy(predatorObject);
                    predatorsToRemove.Add(predatorId);
                }
            }
            foreach (var predatorId in predatorsToRemove) _predators.Remove(predatorId);
        }

        private void OnDestroy()
        {
            Destroy(_predatorMaterial);
        }
    }
}
