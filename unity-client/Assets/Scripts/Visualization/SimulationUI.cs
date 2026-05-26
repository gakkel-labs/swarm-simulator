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
        [SerializeField] private Toggle detectionZoneToggle;
        [SerializeField] private TextMeshProUGUI hudText;

        private float _fpsAccumulator;
        private int _fpsFrameCount;
        private float _currentFps;

        private float _cohesionRefreshTimer;
        private float _displayedSpread;
        private const float CohesionRefreshInterval = 0.2f;

        private void Start()
        {
            if (trailToggle != null)
                trailToggle.onValueChanged.AddListener(visualizer.SetShowTrails);
            if (velocityVectorToggle != null)
                velocityVectorToggle.onValueChanged.AddListener(visualizer.SetShowVelocityVectors);
            if (centroidToggle != null)
                centroidToggle.onValueChanged.AddListener(visualizer.SetShowCentroids);
            if (detectionZoneToggle != null)
                detectionZoneToggle.onValueChanged.AddListener(visualizer.SetShowDetectionZones);
        }

        private void Update()
        {
            if (visualizer == null || hudText == null) return;

            _fpsAccumulator += Time.unscaledDeltaTime;
            _fpsFrameCount++;
            if (_fpsAccumulator >= 0.5f)
            {
                _currentFps     = _fpsFrameCount / _fpsAccumulator;
                _fpsAccumulator = 0f;
                _fpsFrameCount  = 0;
            }

            _cohesionRefreshTimer += Time.unscaledDeltaTime;
            if (_cohesionRefreshTimer >= CohesionRefreshInterval)
            {
                _displayedSpread = visualizer.CohesionSpreadM;
                _cohesionRefreshTimer   = 0f;
            }

            string searchStatusLine = BuildSearchStatusLine();
            hudText.text = $"Agents: {visualizer.AgentCount}\nObstacles: {visualizer.ObstacleCount}\nFPS: {_currentFps:F0}\nSpread: {_displayedSpread:F1}m{searchStatusLine}";
        }

        private string BuildSearchStatusLine()
        {
            if (targetRenderer == null || !targetRenderer.IsPlaced) return string.Empty;

            if (targetRenderer.IsFound)
            {
                string displayAgentId = targetRenderer.FoundByAgentId.Length >= 8
                    ? targetRenderer.FoundByAgentId[..8]
                    : targetRenderer.FoundByAgentId;
                return $"\nFound by {displayAgentId} in {targetRenderer.FoundAtElapsedSeconds:F1}s";
            }

            return $"\nSearching... {targetRenderer.ElapsedSimSeconds:F1}s";
        }
    }
}
