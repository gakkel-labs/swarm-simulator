using System.Collections.Generic;
using Gakkel.Swarm.Contracts.V1;
using UnityEngine;

namespace Gakkel.Swarm.Unity
{
    /// <summary>
    /// Shows each predator of the simulation.
    /// Detailed view: a Greenland shark (Assets/Models/GreenlandShark.glb, made by
    /// tools/models/make_greenland_shark.py), turned towards where it swims.
    /// Simple view: a red sphere, easy to spot.
    /// </summary>
    public class PredatorRenderer : MonoBehaviour
    {
        private const float SimpleSphereDiameter = 2f;
        private const float MinHeadingSqrMagnitude = 1e-4f;

        [Tooltip("Predator model (e.g. Assets/Models/GreenlandShark.glb). Front must be +Z. Empty = red sphere only.")]
        [SerializeField] private GameObject predatorModel;
        [Tooltip("1 = real size (about 4.5 m long).")]
        [SerializeField] private float predatorModelScale = 1f;

        private readonly Dictionary<string, PredatorVisual> _predators = new();
        private Material _predatorMaterial;

        private sealed class PredatorVisual
        {
            public GameObject Root;
            public GameObject Detailed;
            public GameObject Simple;
        }

#if UNITY_EDITOR
        private const string DefaultPredatorModelPath = "Assets/Models/GreenlandShark.glb";

        // Editor only: if nobody has set the model in the Inspector yet, pick the one in the repo.
        // Save the scene (Ctrl+S) once to keep it.
        private void OnValidate()
        {
            if (predatorModel == null)
                predatorModel = UnityEditor.AssetDatabase.LoadAssetAtPath<GameObject>(DefaultPredatorModelPath);
        }
#endif

        private void Awake()
        {
            var shader = Shader.Find("Universal Render Pipeline/Lit");
            _predatorMaterial = new Material(shader) { color = Color.red };
            DetailedView.Changed += ApplyView;
        }

        public void Apply(IList<PredatorState> predators)
        {
            var activeIds = new HashSet<string>();

            foreach (var predatorState in predators)
            {
                activeIds.Add(predatorState.Id);

                if (!_predators.TryGetValue(predatorState.Id, out var predatorVisual))
                {
                    predatorVisual = CreatePredatorVisual();
                    _predators[predatorState.Id] = predatorVisual;
                }

                predatorVisual.Root.transform.position = CoordinateUtils.NedToUnity(predatorState.PositionXyz);
                var heading = CoordinateUtils.NedToUnity(predatorState.VelocityMps);
                if (heading.sqrMagnitude > MinHeadingSqrMagnitude)
                    predatorVisual.Root.transform.rotation = Quaternion.LookRotation(heading, Vector3.up);
            }

            var predatorsToRemove = new List<string>();
            foreach (var (predatorId, predatorVisual) in _predators)
            {
                if (!activeIds.Contains(predatorId))
                {
                    Destroy(predatorVisual.Root);
                    predatorsToRemove.Add(predatorId);
                }
            }
            foreach (var predatorId in predatorsToRemove) _predators.Remove(predatorId);
        }

        private PredatorVisual CreatePredatorVisual()
        {
            var predatorVisual = new PredatorVisual { Root = new GameObject("Predator") };

            predatorVisual.Simple = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            Destroy(predatorVisual.Simple.GetComponent<Collider>());
            predatorVisual.Simple.name = "Simple";
            predatorVisual.Simple.GetComponent<Renderer>().sharedMaterial = _predatorMaterial;
            predatorVisual.Simple.transform.SetParent(predatorVisual.Root.transform, false);
            predatorVisual.Simple.transform.localScale = Vector3.one * SimpleSphereDiameter;

            if (predatorModel != null)
            {
                predatorVisual.Detailed = Instantiate(predatorModel, predatorVisual.Root.transform, false);
                predatorVisual.Detailed.name = "Detailed";
                predatorVisual.Detailed.transform.localScale = Vector3.one * predatorModelScale;
                foreach (var modelCollider in predatorVisual.Detailed.GetComponentsInChildren<Collider>())
                    Destroy(modelCollider);
            }

            ApplyView(predatorVisual, DetailedView.IsOn);
            return predatorVisual;
        }

        private void ApplyView(bool isDetailed)
        {
            foreach (var predatorVisual in _predators.Values) ApplyView(predatorVisual, isDetailed);
        }

        private static void ApplyView(PredatorVisual predatorVisual, bool isDetailed)
        {
            var showDetailed = isDetailed && predatorVisual.Detailed != null;
            if (predatorVisual.Detailed != null) predatorVisual.Detailed.SetActive(showDetailed);
            predatorVisual.Simple.SetActive(!showDetailed);
        }

        private void OnDestroy()
        {
            DetailedView.Changed -= ApplyView;
            Destroy(_predatorMaterial);
        }
    }
}
