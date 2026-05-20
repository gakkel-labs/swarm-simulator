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
        [Tooltip("Distance along camera ray where the target is placed (Unity units)")]
        [SerializeField] private float placementDistance = 50f;

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
            if (!Mouse.current.rightButton.wasPressedThisFrame) return;

            Camera activeCamera = operatorCamera != null ? operatorCamera : Camera.main;
            if (activeCamera == null) return;

            Ray ray = activeCamera.ScreenPointToRay(Mouse.current.position.ReadValue());
            Vector3 unityPosition = ray.GetPoint(placementDistance);

            var nedPosition = new Vec3
            {
                X = unityPosition.z,   // NED North = Unity Z
                Y = unityPosition.x,   // NED East  = Unity X
                Z = -unityPosition.y,  // NED Down  = -Unity Y
            };

            var request = new PlaceTargetRequest { Position = nedPosition };
            var task = _client.PlaceTargetAsync(request).ResponseAsync;
            task.ContinueWith(OnPlaceTargetCompleted);

            Debug.Log($"[PlaceTarget] unity={unityPosition:F1}");
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
