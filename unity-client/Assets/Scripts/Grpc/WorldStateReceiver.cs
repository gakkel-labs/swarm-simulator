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
        [SerializeField] private string serverAddress = "http://localhost:50051";
        [SerializeField] private float retryDelaySeconds = 3f;

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

            _ = ReceiveLoopAsync(_cts.Token);
        }

        private async Task ReceiveLoopAsync(CancellationToken ct)
        {
            while (!ct.IsCancellationRequested)
            {
                try
                {
                    var client = new SwarmObserver.SwarmObserverClient(_channel);
                    using var call = client.SubscribeWorldState(
                        new SubscribeRequest { ClientId = "unity-client" },
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
                catch (RpcException ex) when (ex.StatusCode == StatusCode.Unavailable)
                {
                    MainThreadDispatcher.Enqueue(() =>
                        Debug.LogWarning($"[gRPC] Server disconnected. Retrying in {retryDelaySeconds}s..."));
                    await Task.Delay(TimeSpan.FromSeconds(retryDelaySeconds), ct);
                }
                catch (Exception ex)
                {
                    MainThreadDispatcher.Enqueue(() =>
                        Debug.LogError($"[gRPC] Stream error: {ex.Message}"));
                    return;
                }
            }
        }

        private void OnWorldStateReceived(WorldState ws)
        {
            // Runs on Unity main thread — safe to modify GameObjects here.
            Debug.Log($"[gRPC] WorldState t={ws.TimestampUnixMs} agents={ws.Agents.Count}");
        }

        private void OnDestroy()
        {
            _cts?.Cancel();
            _cts?.Dispose();
            _channel?.Dispose();
        }
    }
}
