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

        private const string ClientId = "unity-client";

        private GrpcChannel _channel;
        private CancellationTokenSource _cts;

        private void Start()
        {
            _cts = new CancellationTokenSource();
            _channel = GrpcChannel.ForAddress(serverAddress, new GrpcChannelOptions
            {
                HttpHandler = new YetAnotherHttpHandler { Http2Only = true },
                DisposeHttpClient = true,
            });

            var task = ReceiveLoopAsync(_cts.Token);
            task.ContinueWith(
                t => Debug.LogException(t.Exception?.InnerException ?? t.Exception, this),
                TaskContinuationOptions.OnlyOnFaulted);
        }

        private async Task ReceiveLoopAsync(CancellationToken ct)
        {
            var client = new SwarmObserver.SwarmObserverClient(_channel);

            while (!ct.IsCancellationRequested)
            {
                try
                {
                    using var call = client.SubscribeWorldState(
                        new SubscribeRequest { ClientId = ClientId },
                        cancellationToken: ct);

                    await foreach (var ws in call.ResponseStream.ReadAllAsync(ct))
                    {
                        var snapshot = ws;
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
                    await Task.Delay(TimeSpan.FromSeconds(retryDelaySeconds), ct);
                }
                catch (Exception ex)
                {
                    MainThreadDispatcher.Enqueue(() =>
                        Debug.LogError($"[gRPC] Unrecoverable stream error:\n{ex}"));
                    return;
                }
            }
        }

        private void OnWorldStateReceived(WorldState ws)
        {
            // Runs on Unity main thread — safe to modify GameObjects here.
#if UNITY_EDITOR
            Debug.Log($"[gRPC] WorldState t={ws.TimestampUnixMs} agents={ws.Agents.Count}");
#endif
        }

        private void OnDestroy()
        {
            _cts?.Cancel();
            _cts?.Dispose();
            _channel?.Dispose();
        }
    }
}
