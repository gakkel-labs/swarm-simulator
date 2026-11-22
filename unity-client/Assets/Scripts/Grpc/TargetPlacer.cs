using System;
using System.Threading.Tasks;
using Gakkel.Swarm.Contracts.V1;
using Grpc.Core;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.InputSystem;

namespace Gakkel.Swarm.Unity
{
    public class TargetPlacer : MonoBehaviour
    {
        [SerializeField] private string serverAddress = SwarmServerChannel.DefaultAddress;
        [SerializeField] private Camera operatorCamera;
        [Tooltip("Distance along camera ray where the target is placed (Unity units)")]
        [SerializeField] private float placementDistance = 50f;
        [Tooltip("Gives the size of the search area. Found automatically if empty.")]
        [SerializeField] private SwarmVisualizer visualizer;

        private SimulationControl.SimulationControlClient _client;

        private void Start()
        {
            if (visualizer == null) visualizer = FindAnyObjectByType<SwarmVisualizer>();
            _client = new SimulationControl.SimulationControlClient(SwarmServerChannel.For(serverAddress));
        }

        private void Update()
        {
            if (Mouse.current == null) return;
            if (!Mouse.current.leftButton.wasPressedThisFrame) return;
            // A click on a HUD check box is for the check box, not for the sea.
            if (EventSystem.current != null && EventSystem.current.IsPointerOverGameObject()) return;

            Camera activeCamera = operatorCamera != null ? operatorCamera : Camera.main;
            if (activeCamera == null) return;

            Ray ray = activeCamera.ScreenPointToRay(Mouse.current.position.ReadValue());
            Vector3 unityPosition = ray.GetPoint(placementDistance);
            // The server refuses a target outside the search area (no drone could find it),
            // so a click beyond the edges, under the seabed or above the surface is moved inside.
            if (visualizer != null) unityPosition = visualizer.ClampToSearchArea(unityPosition);

            var request = new PlaceTargetRequest { Position = CoordinateUtils.UnityToNed(unityPosition) };
#if UNITY_EDITOR
            Debug.Log($"[PlaceTarget] unity={unityPosition:F1}");
#endif
            _ = PlaceTargetAsync(request);
        }

        private async Task PlaceTargetAsync(PlaceTargetRequest request)
        {
            try
            {
                await _client.PlaceTargetAsync(request).ResponseAsync;
            }
            catch (RpcException exception) when (exception.StatusCode == StatusCode.InvalidArgument)
            {
                MainThreadDispatcher.Enqueue(() =>
                    Debug.LogWarning($"[PlaceTarget] Target refused by the server: {exception.Status.Detail}"));
            }
            catch (Exception exception)
            {
                MainThreadDispatcher.Enqueue(() =>
                    Debug.LogError($"[PlaceTarget] RPC failed: {exception.Message}"));
            }
        }
    }
}
