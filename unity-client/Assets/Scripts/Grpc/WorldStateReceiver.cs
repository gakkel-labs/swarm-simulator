using System;
using System.Threading;
using System.Threading.Tasks;
using Cysharp.Net.Http;
using Gakkel.Swarm.Contracts.V1;
using Grpc.Core;
using Grpc.Net.Client;
using UnityEngine;

namespace Gakkel.Swarm.Unity
{
    public class WorldStateReceiver : MonoBehaviour
    {
        [Tooltip("Use http:// (cleartext H2) for local dev; https:// requires a TLS cert.")]
        [SerializeField] private string serverAddress = "http://localhost:50051";
        [SerializeField] private float retryDelaySeconds = 3f;
        [SerializeField] private SwarmVisualizer visualizer;
        [SerializeField] private TargetRenderer targetRenderer;

        private const string ClientId = "unity-client";

        private GrpcChannel _channel;
        private CancellationTokenSource _cancellationTokenSource;

        private void Start()
        {
            _cancellationTokenSource = new CancellationTokenSource();
            _channel = GrpcChannel.ForAddress(serverAddress, new GrpcChannelOptions
            {
                HttpHandler = new YetAnotherHttpHandler { Http2Only = true },
                DisposeHttpClient = true,
            });

            var receiveTask = ReceiveLoopAsync(_cancellationTokenSource.Token);
            receiveTask.ContinueWith(
                task => Debug.LogException(task.Exception?.InnerException ?? task.Exception, this),
                TaskContinuationOptions.OnlyOnFaulted);
        }

        private async Task ReceiveLoopAsync(CancellationToken cancellationToken)
        {
            var client = new SwarmObserver.SwarmObserverClient(_channel);

            while (!cancellationToken.IsCancellationRequested)
            {
                try
                {
                    using var call = client.SubscribeWorldState(
                        new SubscribeRequest { ClientId = ClientId },
                        cancellationToken: cancellationToken);

                    await foreach (var worldState in call.ResponseStream.ReadAllAsync(cancellationToken))
                    {
                        var snapshot = worldState;
                        MainThreadDispatcher.Enqueue(() => OnWorldStateReceived(snapshot));
                    }
                }
                catch (RpcException ex) when (ex.StatusCode == StatusCode.Cancelled)
                {
                    // Normal stop via OnDestroy — no log needed.
                    return;
                }
                catch (RpcException ex) when (
                    ex.StatusCode == StatusCode.Unavailable ||
                    ex.StatusCode == StatusCode.Unknown ||
                    ex.StatusCode == StatusCode.Internal)
                {
                    MainThreadDispatcher.Enqueue(() =>
                        Debug.LogWarning($"[gRPC] Server disconnected ({ex.StatusCode}). Retrying in {retryDelaySeconds}s..."));
                    await Task.Delay(TimeSpan.FromSeconds(retryDelaySeconds), cancellationToken);
                }
                catch (Exception ex)
                {
                    MainThreadDispatcher.Enqueue(() =>
                        Debug.LogError($"[gRPC] Unrecoverable stream error:\n{ex}"));
                    return;
                }
            }
        }

        private void OnWorldStateReceived(WorldState worldState)
        {
            visualizer?.Apply(worldState);
            targetRenderer?.Apply(worldState.SearchStatus);
#if UNITY_EDITOR
            Debug.Log($"[gRPC] WorldState t={worldState.TimestampUnixMs} agents={worldState.Agents.Count}");
#endif
        }

        private void OnDestroy()
        {
            _cancellationTokenSource?.Cancel();
            _cancellationTokenSource?.Dispose();
            _channel?.Dispose();
        }
    }
}
