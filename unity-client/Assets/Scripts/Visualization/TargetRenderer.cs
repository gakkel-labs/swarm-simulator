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
        private bool _isBlinking;

        public bool IsPlaced               => _sphere.activeSelf;
        public bool IsFound                { get; private set; }
        public float ElapsedSimSeconds     { get; private set; }
        public string FoundByAgentId       { get; private set; }
        public float FoundAtElapsedSeconds { get; private set; }

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
            if (!_isBlinking) return;
            float elapsed = Time.time - _blinkStartTime;
            if (elapsed > BlinkDuration)
            {
                _sphereRenderer.material.color = TargetColor;
                _isBlinking = false;
                return;
            }
            _sphereRenderer.material.color = Mathf.Sin(elapsed * BlinkFrequency) > 0 ? TargetColor : Color.white;
        }

        public void Apply(SearchStatus searchStatus)
        {
            if (searchStatus == null || !searchStatus.TargetPlaced)
            {
                _sphere.SetActive(false);
                IsFound = false;
                return;
            }

            ElapsedSimSeconds = searchStatus.ElapsedSimS;
            _sphere.transform.position = CoordinateUtils.NedToUnity(searchStatus.TargetPosition);
            _sphere.SetActive(true);

            if (searchStatus.FoundEvent == null)
            {
                IsFound        = false;
                FoundByAgentId = null;
                return;
            }

            if (!IsFound)
            {
                IsFound                = true;
                FoundByAgentId         = searchStatus.FoundEvent.AgentId;
                FoundAtElapsedSeconds  = searchStatus.FoundEvent.ElapsedSimS;
                _blinkStartTime        = Time.time;
                _isBlinking            = true;
            }
        }

        public static Color ComputeBlinkColor(Color baseColor, float startTime, float duration = 5f, float frequency = 10f)
        {
            float elapsed = Time.time - startTime;
            if (elapsed > duration) return baseColor;
            return Mathf.Sin(elapsed * frequency) > 0 ? baseColor : Color.white;
        }

        private void OnDestroy()
        {
            if (_sphere != null) Destroy(_sphere);
        }
    }
}
