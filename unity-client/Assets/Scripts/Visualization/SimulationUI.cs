using TMPro;
using UnityEngine;
using UnityEngine.UI;

namespace Gakkel.Swarm.Unity
{
    public class SimulationUI : MonoBehaviour
    {
        [SerializeField] private SwarmVisualizer visualizer;
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
            if (hudText == null) return;

            _fpsAccum += Time.unscaledDeltaTime;
            _fpsFrames++;
            if (_fpsAccum >= 0.5f)
            {
                _fpsCurrent = _fpsFrames / _fpsAccum;
                _fpsAccum = 0f;
                _fpsFrames = 0;
            }

            hudText.text = $"Agents: {visualizer.AgentCount}\nObstacles: {visualizer.ObstacleCount}\nFPS: {_fpsCurrent:F0}";
        }
    }
}
