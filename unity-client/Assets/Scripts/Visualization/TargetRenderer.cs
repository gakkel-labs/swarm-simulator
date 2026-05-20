using Gakkel.Swarm.Contracts.V1;
using UnityEngine;

namespace Gakkel.Swarm.Unity
{
    public class TargetRenderer : MonoBehaviour
    {
        private static readonly Color TargetColor = Color.green;
        private const float BlinkDuration = 5f;
        private const float BlinkFrequency = 10f;

        private GameObject _sphere;
        private Renderer _sphereRenderer;
        private float _blinkStartTime;

        public bool IsPlaced         { get; private set; }
        public bool IsFound          { get; private set; }
        public float ElapsedSimS     { get; private set; }
        public string FoundByAgentId { get; private set; }
        public float FoundAtElapsedS { get; private set; }

        private void Awake()
        {
            _sphere = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            Destroy(_sphere.GetComponent<Collider>());
            _sphere.name = "SAR_Target";
            _sphereRenderer = _sphere.GetComponent<Renderer>();
            var shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null) Debug.LogWarning("[TargetRenderer] URP/Lit shader not found — check URP package.");
            _sphereRenderer.material = new Material(shader != null ? shader : Shader.Find("Standard")) { color = TargetColor };
            _sphere.transform.localScale = Vector3.one * 3f;
            _sphere.SetActive(false);
        }

        private void Update()
        {
            if (IsFound)
                _sphereRenderer.material.color = ComputeBlinkColor(TargetColor, _blinkStartTime, BlinkDuration, BlinkFrequency);
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

            if (searchStatus.FoundEvent != null && !IsFound)
            {
                IsFound          = true;
                FoundByAgentId   = searchStatus.FoundEvent.AgentId;
                FoundAtElapsedS  = searchStatus.FoundEvent.ElapsedSimS;
                _blinkStartTime  = Time.time;
            }
        }

        public static Color ComputeBlinkColor(Color baseColor, float startTime, float duration = 5f, float frequency = 10f)
        {
            if (Time.time - startTime > duration) return baseColor;
            return Mathf.Sin((Time.time - startTime) * frequency) > 0 ? baseColor : Color.white;
        }

        private static Vector3 NedToUnity(Vec3 ned) => new(ned.Y, -ned.Z, ned.X);

        private void OnDestroy()
        {
            if (_sphere != null) Destroy(_sphere);
        }
    }
}
