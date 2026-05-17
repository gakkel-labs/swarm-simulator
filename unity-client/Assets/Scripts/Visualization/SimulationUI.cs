using UnityEngine;
using UnityEngine.UI;

namespace Gakkel.Swarm.Unity
{
    public class SimulationUI : MonoBehaviour
    {
        [SerializeField] private SwarmVisualizer visualizer;
        [SerializeField] private Toggle trailToggle;
        [SerializeField] private Toggle velocityVectorToggle;

        private void Start()
        {
            if (trailToggle != null)
                trailToggle.onValueChanged.AddListener(visualizer.SetShowTrails);
            if (velocityVectorToggle != null)
                velocityVectorToggle.onValueChanged.AddListener(visualizer.SetShowVelocityVectors);
        }
    }
}
