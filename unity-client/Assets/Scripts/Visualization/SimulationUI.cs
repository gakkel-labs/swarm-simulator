using TMPro;
using UnityEngine;
using UnityEngine.UI;

namespace Gakkel.Swarm.Unity
{
    public class SimulationUI : MonoBehaviour
    {
        [SerializeField] private SwarmVisualizer visualizer;
        [SerializeField] private TargetRenderer targetRenderer;
        [SerializeField] private Toggle trailToggle;
        [SerializeField] private Toggle velocityVectorToggle;
        [SerializeField] private Toggle centroidToggle;
        [SerializeField] private TextMeshProUGUI hudText;

        private float _fpsAccum;
        private int _fpsFrames;
        private float _fpsCurrent;

        private void Start()
        {
            if (trailToggle != null)
                trailToggle.onValueChanged.AddListener(visualizer.SetShowTrails);
            if (velocityVectorToggle != null)
                velocityVectorToggle.onValueChanged.AddListener(visualizer.SetShowVelocityVectors);
            if (centroidToggle != null)
                centroidToggle.onValueChanged.AddListener(visualizer.SetShowCentroids);
        }

        private void Update()
        {
            if (visualizer == null || hudText == null) return;

            _fpsAccum += Time.unscaledDeltaTime;
            _fpsFrames++;
            if (_fpsAccum >= 0.5f)
            {
                _fpsCurrent = _fpsFrames / _fpsAccum;
                _fpsAccum = 0f;
                _fpsFrames = 0;
            }

            string sarLine = BuildSarLine();
            hudText.text = $"Agents: {visualizer.AgentCount}\nObstacles: {visualizer.ObstacleCount}\nFPS: {_fpsCurrent:F0}{sarLine}";
        }

        private string BuildSarLine()
        {
            if (targetRenderer == null || !targetRenderer.IsPlaced) return string.Empty;

            if (targetRenderer.IsFound)
            {
                string shortId = targetRenderer.FoundByAgentId.Length >= 8
                    ? targetRenderer.FoundByAgentId[..8]
                    : targetRenderer.FoundByAgentId;
                return $"\nFound by {shortId} in {targetRenderer.FoundAtElapsedS:F1}s";
            }

            return $"\nSearching... {targetRenderer.ElapsedSimS:F1}s";
        }
    }
}
