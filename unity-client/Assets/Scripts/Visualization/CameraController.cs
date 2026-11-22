using System;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.InputSystem;

namespace Gakkel.Swarm.Unity
{
    // Three cameras on one Camera object:
    // - Overview: orbits around the centre of the swarm (whole search area in view).
    // - Follow:   chase camera behind and a bit above one drone, so its neighbours stay visible.
    // - Free:     fly with W A S D and the mouse.
    // Keys: F = overview <-> follow, N = next drone (Shift+N = previous), Tab = free, R = reset.
    public class CameraController : MonoBehaviour
    {
        [SerializeField] private SwarmVisualizer visualizer;

        [Header("Overview")]
        [SerializeField] private float distance = 25f;
        [SerializeField] private float autoOrbitSpeed = 10f;
        [SerializeField] private float minDistance = 5f;
        [SerializeField] private float maxDistance = 100f;

        [Header("Follow a drone")]
        [SerializeField] private float followDistance = 8f;
        [SerializeField] private float followHeight = 3f;
        [SerializeField] private float followLookAhead = 4f;
        [SerializeField] private float followMinDistance = 3f;
        [SerializeField] private float followMaxDistance = 40f;
        [Tooltip("Time in seconds for the camera to catch up with the drone. Bigger = smoother.")]
        [SerializeField] private float followPositionSmoothTime = 0.5f;
        [Tooltip("Time in seconds for the camera to follow the drone going up or down. Bigger = smoother.")]
        [SerializeField] private float followVerticalSmoothTime = 1.2f;
        [Tooltip("How fast the camera turns toward the drone. Smaller = smoother.")]
        [SerializeField] private float followRotationSharpness = 4f;
        [Tooltip("How fast the camera swings behind the drone when it turns. Smaller = smoother.")]
        [SerializeField] private float followHeadingSharpness = 1.5f;
        [SerializeField] private float followMinHeightAboveSeabed = 1f;

        [Header("Free")]
        [SerializeField] private float freeMoveSpeed = 20f;

        [Header("Mouse")]
        [SerializeField] private float mouseSensitivity = 0.3f;
        [SerializeField] private float zoomSensitivity = 2f;

        private enum CameraMode { Overview, Follow, Free }
        private CameraMode _mode = CameraMode.Overview;

        private const float DefaultOverviewPitch = 30f;
        private const float MinHeadingSqrMagnitude = 1e-4f;
        private const int ShortDroneIdLength = 8;
        // The server sends positions 20 times per second: never guess further ahead than two updates.
        private const float MaxExtrapolationSeconds = 0.1f;

        private float _defaultDistance;
        private float _defaultFollowDistance;
        private float _yaw;
        private float _pitch = DefaultOverviewPitch;

        private string _followedDroneId;
        private Vector3 _followHeading = Vector3.forward;
        private Vector3 _followSmoothingVelocity;
        private Vector3 _followAnchor;
        private float _followAnchorVerticalSpeed;
        private Vector3 _lastReceivedDronePosition;
        private float _secondsSinceDronePositionReceived;

        private Vector3 _freePosition;
        private float _freeYaw;
        private float _freePitch;

        private readonly List<string> _sortedDroneIds = new();

        // Short text for the HUD, for example "follow drone 1a2b3c4d (N next, F overview)".
        public string ModeLabel => _mode switch
        {
            CameraMode.Follow => $"follow drone {ShortId(_followedDroneId)} (N next, F overview)",
            CameraMode.Free   => "free (Tab overview)",
            _                 => "overview (F follow, Tab free)",
        };

        private void Awake()
        {
            _defaultDistance       = distance;
            _defaultFollowDistance = followDistance;
        }

        private void LateUpdate()
        {
            var keyboard = Keyboard.current;
            if (keyboard != null && keyboard.fKey.wasPressedThisFrame)
                ToggleFollow();
            if (keyboard != null && keyboard.tabKey.wasPressedThisFrame)
                ToggleFree();

            switch (_mode)
            {
                case CameraMode.Overview: UpdateOverview(keyboard); break;
                case CameraMode.Follow:   UpdateFollow(keyboard);   break;
                case CameraMode.Free:     UpdateFree(keyboard);     break;
            }
        }

        private void ToggleFollow()
        {
            if (_mode == CameraMode.Follow)
            {
                _mode = CameraMode.Overview;
                return;
            }
            if (visualizer == null || !SelectDroneClosestToSwarmCentre()) return;
            _followSmoothingVelocity = Vector3.zero;
            _mode = CameraMode.Follow;
        }

        private void ToggleFree()
        {
            if (_mode == CameraMode.Free)
            {
                _mode = CameraMode.Overview;
                return;
            }
            var angles = transform.eulerAngles;
            _freePosition = transform.position;
            _freeYaw      = angles.y;
            _freePitch    = Mathf.DeltaAngle(0f, angles.x);
            _mode = CameraMode.Free;
        }

        private void UpdateOverview(Keyboard keyboard)
        {
            if (keyboard != null && keyboard.rKey.wasPressedThisFrame)
            {
                _yaw = 0f;
                _pitch = DefaultOverviewPitch;
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

        private void UpdateFollow(Keyboard keyboard)
        {
            if (keyboard != null && keyboard.nKey.wasPressedThisFrame)
                SelectNeighbourDrone(keyboard.shiftKey.isPressed ? -1 : 1);
            if (keyboard != null && keyboard.rKey.wasPressedThisFrame)
                followDistance = _defaultFollowDistance;

            // The followed drone may be gone (simulation restarted): pick another one, or go back to overview.
            if (!visualizer.TryGetDroneMotion(_followedDroneId, out var dronePosition, out var droneVelocity))
            {
                if (!SelectDroneClosestToSwarmCentre()) _mode = CameraMode.Overview;
                return;
            }

            var mouse = Mouse.current;
            if (mouse != null)
            {
                float scroll = mouse.scroll.ReadValue().y;
                if (Mathf.Abs(scroll) > 0.001f)
                    followDistance = Mathf.Clamp(followDistance - scroll * zoomSensitivity,
                                                 followMinDistance, followMaxDistance);
            }

            float deltaTime = Time.deltaTime;
            UpdateFollowHeading(droneVelocity, deltaTime);

            UpdateFollowAnchor(dronePosition, droneVelocity, deltaTime);

            var wantedPosition = _followAnchor - _followHeading * followDistance + Vector3.up * followHeight;
            float seabedLimit = visualizer.SeabedHeightAt(wantedPosition) + followMinHeightAboveSeabed;
            wantedPosition.y = Mathf.Max(wantedPosition.y, seabedLimit);
            transform.position = Vector3.SmoothDamp(transform.position, wantedPosition,
                                                    ref _followSmoothingVelocity, followPositionSmoothTime);

            var lookTarget = _followAnchor + _followHeading * followLookAhead;
            var lookDirection = lookTarget - transform.position;
            if (lookDirection.sqrMagnitude < MinHeadingSqrMagnitude) return;
            var wantedRotation = Quaternion.LookRotation(lookDirection, Vector3.up);
            transform.rotation = Quaternion.Slerp(transform.rotation, wantedRotation,
                                                  SmoothingFactor(followRotationSharpness, deltaTime));
        }

        // The anchor is the point the camera follows and looks at. Two fixes against jerks:
        // 1. Positions arrive only 20 times per second, so between two updates we move the anchor
        //    along the drone velocity (a guess called "dead reckoning") instead of jumping.
        // 2. The height is smoothed much more than the rest, so the camera does not bob up and
        //    down each time a drone climbs or dives a little (Boids drones do it all the time).
        private void UpdateFollowAnchor(Vector3 dronePosition, Vector3 droneVelocity, float deltaTime)
        {
            if (dronePosition != _lastReceivedDronePosition)
            {
                _lastReceivedDronePosition          = dronePosition;
                _secondsSinceDronePositionReceived = 0f;
            }
            else
            {
                _secondsSinceDronePositionReceived += deltaTime;
            }

            float extrapolationSeconds = Mathf.Min(_secondsSinceDronePositionReceived, MaxExtrapolationSeconds);
            var estimatedPosition = dronePosition + droneVelocity * extrapolationSeconds;

            float smoothedHeight = Mathf.SmoothDamp(_followAnchor.y, estimatedPosition.y,
                                                    ref _followAnchorVerticalSpeed, followVerticalSmoothTime);
            _followAnchor = new Vector3(estimatedPosition.x, smoothedHeight, estimatedPosition.z);
        }

        // The camera stays behind the drone's horizontal heading. The heading turns slowly so the
        // camera does not shake when the drone changes direction quickly (Boids drones often do).
        private void UpdateFollowHeading(Vector3 droneVelocity, float deltaTime)
        {
            var horizontalVelocity = new Vector3(droneVelocity.x, 0f, droneVelocity.z);
            if (horizontalVelocity.sqrMagnitude < MinHeadingSqrMagnitude) return;
            _followHeading = Vector3.Slerp(_followHeading, horizontalVelocity.normalized,
                                           SmoothingFactor(followHeadingSharpness, deltaTime)).normalized;
        }

        // Frame-rate independent smoothing: same result at 30 or 144 frames per second.
        private static float SmoothingFactor(float sharpness, float deltaTime)
            => 1f - Mathf.Exp(-sharpness * deltaTime);

        // Start with the drone nearest to the swarm centre: it has the most neighbours around it.
        private bool SelectDroneClosestToSwarmCentre()
        {
            var centroid = visualizer.GetCentroid();
            string closestDroneId = null;
            float closestSqrDistance = float.MaxValue;
            Vector3 closestDroneVelocity = Vector3.zero;
            foreach (var droneId in visualizer.DroneIds)
            {
                if (!visualizer.TryGetDroneMotion(droneId, out var position, out var velocity)) continue;
                float sqrDistance = (position - centroid).sqrMagnitude;
                if (sqrDistance >= closestSqrDistance) continue;
                closestSqrDistance   = sqrDistance;
                closestDroneId       = droneId;
                closestDroneVelocity = velocity;
            }
            if (closestDroneId == null) return false;
            StartFollowing(closestDroneId, closestDroneVelocity);
            return true;
        }

        // Drones are sorted by id so N always goes through them in the same order.
        private void SelectNeighbourDrone(int step)
        {
            _sortedDroneIds.Clear();
            _sortedDroneIds.AddRange(visualizer.DroneIds);
            if (_sortedDroneIds.Count == 0) return;
            _sortedDroneIds.Sort(StringComparer.Ordinal);

            int currentIndex = _sortedDroneIds.IndexOf(_followedDroneId);
            int count = _sortedDroneIds.Count;
            int nextIndex = ((currentIndex + step) % count + count) % count;
            var nextDroneId = _sortedDroneIds[nextIndex];
            visualizer.TryGetDroneMotion(nextDroneId, out _, out var velocity);
            StartFollowing(nextDroneId, velocity);
        }

        private void StartFollowing(string droneId, Vector3 droneVelocity)
        {
            _followedDroneId = droneId;
            if (visualizer.TryGetDroneMotion(droneId, out var dronePosition, out _))
            {
                _followAnchor                       = dronePosition;
                _lastReceivedDronePosition          = dronePosition;
                _secondsSinceDronePositionReceived = 0f;
                _followAnchorVerticalSpeed          = 0f;
            }
            var horizontalVelocity = new Vector3(droneVelocity.x, 0f, droneVelocity.z);
            if (horizontalVelocity.sqrMagnitude >= MinHeadingSqrMagnitude)
                _followHeading = horizontalVelocity.normalized;
        }

        private static string ShortId(string droneId)
        {
            if (string.IsNullOrEmpty(droneId)) return "-";
            return droneId.Length > ShortDroneIdLength ? droneId[..ShortDroneIdLength] : droneId;
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
