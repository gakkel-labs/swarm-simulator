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
            var keyboard = Keyboard.current;
            if (keyboard != null && keyboard.tabKey.wasPressedThisFrame)
                SwitchMode();

            if (_mode == CameraMode.Orbital)
                UpdateOrbital(keyboard);
            else
                UpdateFree(keyboard);
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

        private void UpdateOrbital(Keyboard keyboard)
        {
            if (keyboard != null && keyboard.rKey.wasPressedThisFrame)
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

        private void UpdateFree(Keyboard keyboard)
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

            var rotation = Quaternion.Euler(_freePitch, _freeYaw, 0);
            if (keyboard != null)
            {
                float speed = freeMoveSpeed * Time.deltaTime;
                if (keyboard.wKey.isPressed) _freePosition += rotation * Vector3.forward * speed;
                if (keyboard.sKey.isPressed) _freePosition -= rotation * Vector3.forward * speed;
                if (keyboard.aKey.isPressed) _freePosition -= rotation * Vector3.right   * speed;
                if (keyboard.dKey.isPressed) _freePosition += rotation * Vector3.right   * speed;
            }

            transform.position = _freePosition;
            transform.rotation = rotation;
        }
    }
}
