using UnityEngine;
using UnityEngine.InputSystem;

namespace Gakkel.Swarm.Unity
{
    public class CameraController : MonoBehaviour
    {
        [SerializeField] private SwarmVisualizer visualizer;
        [SerializeField] private float distance = 25f;
        [SerializeField] private float autoOrbitSpeed = 10f;
        [SerializeField] private float mouseSensitivity = 0.3f;
        [SerializeField] private float zoomSensitivity = 2f;
        [SerializeField] private float minDistance = 5f;
        [SerializeField] private float maxDistance = 100f;
        [SerializeField] private float freeMoveSpeed = 20f;

        private enum CameraMode { Orbital, Free }
        private CameraMode _mode = CameraMode.Orbital;

        private float _defaultDistance;
        private float _yaw;
        private float _pitch = 30f;

        private Vector3 _freePosition;
        private float _freeYaw;
        private float _freePitch;

        private void Awake()
        {
            _defaultDistance = distance;
        }

        private void LateUpdate()
        {
            var kb = Keyboard.current;
            if (kb != null && kb.tabKey.wasPressedThisFrame)
                SwitchMode();

            if (_mode == CameraMode.Orbital)
                UpdateOrbital(kb);
            else
                UpdateFree(kb);
        }

        private void SwitchMode()
        {
            if (_mode == CameraMode.Orbital)
            {
                _freePosition = transform.position;
                _freeYaw = _yaw;
                _freePitch = _pitch;
                _mode = CameraMode.Free;
            }
            else
            {
                _mode = CameraMode.Orbital;
            }
        }

        private void UpdateOrbital(Keyboard kb)
        {
            if (kb != null && kb.rKey.wasPressedThisFrame)
            {
                _yaw = 0f;
                _pitch = 30f;
                distance = _defaultDistance;
            }

            var mouse = Mouse.current;
            if (mouse == null) return;

            if (mouse.rightButton.isPressed)
            {
                var delta = mouse.delta.ReadValue();
                _yaw   += delta.x * mouseSensitivity;
                _pitch -= delta.y * mouseSensitivity;
                _pitch  = Mathf.Clamp(_pitch, -80f, 80f);
            }
            else
            {
                _yaw += autoOrbitSpeed * Time.deltaTime;
            }

            float scroll = mouse.scroll.ReadValue().y;
            if (Mathf.Abs(scroll) > 0.001f)
                distance = Mathf.Clamp(distance - scroll * zoomSensitivity, minDistance, maxDistance);

            var centroid = visualizer != null ? visualizer.GetCentroid() : Vector3.zero;
            transform.position = centroid + Quaternion.Euler(_pitch, _yaw, 0) * new Vector3(0, 0, -distance);
            transform.LookAt(centroid);
        }

        private void UpdateFree(Keyboard kb)
        {
            var mouse = Mouse.current;
            if (mouse != null)
            {
                if (mouse.rightButton.isPressed)
                {
                    var delta = mouse.delta.ReadValue();
                    _freeYaw   += delta.x * mouseSensitivity;
                    _freePitch -= delta.y * mouseSensitivity;
                    _freePitch  = Mathf.Clamp(_freePitch, -89f, 89f);
                }

                float scroll = mouse.scroll.ReadValue().y;
                if (Mathf.Abs(scroll) > 0.001f)
                    _freePosition += Quaternion.Euler(_freePitch, _freeYaw, 0) * Vector3.forward * scroll * zoomSensitivity;
            }

            var rot = Quaternion.Euler(_freePitch, _freeYaw, 0);
            if (kb != null)
            {
                float speed = freeMoveSpeed * Time.deltaTime;
                if (kb.wKey.isPressed) _freePosition += rot * Vector3.forward * speed;
                if (kb.sKey.isPressed) _freePosition -= rot * Vector3.forward * speed;
                if (kb.aKey.isPressed) _freePosition -= rot * Vector3.right   * speed;
                if (kb.dKey.isPressed) _freePosition += rot * Vector3.right   * speed;
            }

            transform.position = _freePosition;
            transform.rotation = rot;
        }
    }
}
