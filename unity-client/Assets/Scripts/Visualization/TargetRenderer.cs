using Gakkel.Swarm.Contracts.V1;
using UnityEngine;

namespace Gakkel.Swarm.Unity
{
    public class TargetRenderer : MonoBehaviour
    {
        private GameObject _sphere;

        public bool IsPlaced      { get; private set; }
        public bool IsFound       { get; private set; }
        public float ElapsedSimS  { get; private set; }
        public string FoundByAgentId { get; private set; }
        public float FoundAtElapsedS { get; private set; }

        private void Awake()
        {
            _sphere = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            Destroy(_sphere.GetComponent<Collider>());
            _sphere.name = "SAR_Target";
            _sphere.GetComponent<Renderer>().material =
                new Material(Shader.Find("Universal Render Pipeline/Lit")) { color = Color.green };
            _sphere.transform.localScale = Vector3.one * 3f;
            _sphere.SetActive(false);
        }

        public void Apply(SearchStatus searchStatus)
        {
            if (searchStatus == null || !searchStatus.TargetPlaced)
            {
                _sphere.SetActive(false);
                IsPlaced = false;
                return;
            }

            IsPlaced    = true;
            ElapsedSimS = searchStatus.ElapsedSimS;
            _sphere.transform.position = NedToUnity(searchStatus.TargetPosition);
            _sphere.SetActive(true);

            if (searchStatus.FoundEvent != null)
            {
                IsFound          = true;
                FoundByAgentId   = searchStatus.FoundEvent.AgentId;
                FoundAtElapsedS  = searchStatus.FoundEvent.ElapsedSimS;
            }
        }

        private static Vector3 NedToUnity(Vec3 ned) => new(ned.Y, -ned.Z, ned.X);

        private void OnDestroy()
        {
            if (_sphere != null) Destroy(_sphere);
        }
    }
}
