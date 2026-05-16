using UnityEngine;

namespace Gakkel.Swarm.Unity
{
    public class OrbitalCamera : MonoBehaviour
    {
        [SerializeField] private SwarmVisualizer visualizer;
        [SerializeField] private float distance = 25f;
        [SerializeField] private float orbitSpeed = 15f;
        [SerializeField] private float elevation = 30f;

        private float _angle;

        private void LateUpdate()
        {
            if (visualizer == null) return;
            var centroid = visualizer.GetCentroid();
            _angle += orbitSpeed * Time.deltaTime;
            transform.position = centroid
                + Quaternion.Euler(elevation, _angle, 0) * new Vector3(0, 0, -distance);
            transform.LookAt(centroid);
        }
    }
}
