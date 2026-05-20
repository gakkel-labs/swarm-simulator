using System.Threading.Tasks;
using Cysharp.Net.Http;
using Gakkel.Swarm.Contracts.V1;
using Grpc.Net.Client;
using UnityEngine;
using UnityEngine.InputSystem;

namespace Gakkel.Swarm.Unity
{
    public class TargetPlacer : MonoBehaviour
    {
        [SerializeField] private string serverAddress = "http://localhost:50051";
        [SerializeField] private Camera operatorCamera;
        [Tooltip("Scroll sensitivity for adjusting placement depth (Unity Y units per scroll tick)")]
        [SerializeField] private float scrollSensitivity = 3f;

        // Unity Y range: 0 (surface) to -100 (sea floor). Default: mid-depth.
        private float _placementDepthY = -50f;
        private const float DepthMin = -100f;
        private const float DepthMax = 0f;

        private SimulationControl.SimulationControlClient _client;
        private GrpcChannel _channel;

        private void Start()
        {
            _channel = GrpcChannel.ForAddress(serverAddress, new GrpcChannelOptions
            {
                HttpHandler = new YetAnotherHttpHandler { Http2Only = true },
                DisposeHttpClient = true,
            });
            _client = new SimulationControl.SimulationControlClient(_channel);
        }

        private void Update()
        {
            if (Mouse.current == null) return;

            float scrollDelta = Mouse.current.scroll.ReadValue().y;
            if (scrollDelta != 0f)
                _placementDepthY = Mathf.Clamp(_placementDepthY + scrollDelta * scrollSensitivity, DepthMin, DepthMax);

            if (!Mouse.current.leftButton.wasPressedThisFrame) return;

            Camera activeCamera = operatorCamera != null ? operatorCamera : Camera.main;
            if (activeCamera == null) return;

            Ray ray = activeCamera.ScreenPointToRay(Mouse.current.position.ReadValue());
            var horizontalPlane = new Plane(Vector3.up, new Vector3(0f, _placementDepthY, 0f));

            if (!horizontalPlane.Raycast(ray, out float distance)) return;

            Vector3 unityPosition = ray.GetPoint(distance);
            var nedPosition = new Vec3
            {
                X = unityPosition.z,   // NED North = Unity Z
                Y = unityPosition.x,   // NED East  = Unity X
                Z = -unityPosition.y,  // NED Down  = -Unity Y
            };

            var request = new PlaceTargetRequest { Position = nedPosition };
            var task = _client.PlaceTargetAsync(request).ResponseAsync;
            task.ContinueWith(OnPlaceTargetCompleted);

            Debug.Log($"[PlaceTarget] depth={_placementDepthY:F0}m unity={unityPosition}");
        }

        private static void OnPlaceTargetCompleted(Task<PlaceTargetResponse> task)
        {
            if (task.IsFaulted)
                Debug.LogError($"[PlaceTarget] RPC failed: {task.Exception?.InnerException?.Message}");
        }

        private void OnDestroy()
        {
            _channel?.Dispose();
        }
    }
}
