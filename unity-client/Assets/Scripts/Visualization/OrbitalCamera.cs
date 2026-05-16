using UnityEngine;

namespace Gakkel.Swarm.Unity
{
    public class OrbitalCamera : MonoBehaviour
    {
        [SerializeField] private SwarmVisualizer visualizer;
        [SerializeField] private float distance = 25f;
        [SerializeField] private float autoOrbitSpeed = 10f;
        [SerializeField] private float mouseSensitivity = 3f;
        [SerializeField] private float zoomSensitivity = 2f;
        [SerializeField] private float minDistance = 5f;
        [SerializeField] private float maxDistance = 100f;

        private float _yaw;
        private float _pitch = 30f;

        private void LateUpdate()
        {
            HandleInput();

            var centroid = visualizer != null ? visualizer.GetCentroid() : Vector3.zero;
            transform.position = centroid
                + Quaternion.Euler(_pitch, _yaw, 0) * new Vector3(0, 0, -distance);
            transform.LookAt(centroid);
        }

        private void HandleInput()
        {
            // Orbit avec clic gauche
            if (Input.GetMouseButton(0))
            {
                _yaw   += Input.GetAxis("Mouse X") * mouseSensitivity;
                _pitch -= Input.GetAxis("Mouse Y") * mouseSensitivity;
                _pitch  = Mathf.Clamp(_pitch, -80f, 80f);
            }
            else
            {
                // Auto-orbite quand pas d'input
                _yaw += autoOrbitSpeed * Time.deltaTime;
            }

            // Zoom molette
            float scroll = Input.GetAxis("Mouse ScrollWheel");
            if (Mathf.Abs(scroll) > 0.001f)
                distance = Mathf.Clamp(distance - scroll * zoomSensitivity * 10f, minDistance, maxDistance);
        }
    }
}
