using System;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;
using Cysharp.Net.Http;
using Grpc.Net.Client;
using UnityEngine;

namespace Gakkel.Swarm.Unity
{
    // Probes HTTP/2 reachability on serverAddress without opening the gRPC stream.
    // Grpc.Net.Client connects lazily, so we send a HEAD request instead.
    // Attach to any GameObject; configure serverAddress and timeout in the Inspector.
    public class GrpcConnectionTest : MonoBehaviour
    {
        [SerializeField] private string serverAddress = "http://localhost:50051";
        [SerializeField] private double connectTimeoutSeconds = 3.0;

        private GrpcChannel _channel;
        private CancellationTokenSource _cts;

        private async void Start()
        {
            _cts = new CancellationTokenSource(TimeSpan.FromSeconds(connectTimeoutSeconds));
            try
            {
                var channelHandler = new YetAnotherHttpHandler { Http2Only = true };
                _channel = GrpcChannel.ForAddress(serverAddress, new GrpcChannelOptions
                {
                    HttpHandler = channelHandler,
                    DisposeHttpClient = true,
                });

                // Probe TCP reachability: the gRPC server rejects HEAD with 405 but
                // that proves HTTP/2 connectivity is up.
                using var probeHandler = new YetAnotherHttpHandler { Http2Only = true };
                using var probeClient = new HttpClient(probeHandler, disposeHandler: false);
                probeClient.Timeout = TimeSpan.FromSeconds(connectTimeoutSeconds);
                var response = await probeClient.SendAsync(
                    new HttpRequestMessage(HttpMethod.Head, serverAddress), _cts.Token);

                Debug.Log($"[gRPC] Server reachable at {serverAddress} (HTTP {(int)response.StatusCode})");
            }
            catch (OperationCanceledException)
            {
                Debug.LogWarning($"[gRPC] Probe to {serverAddress} timed out after {connectTimeoutSeconds}s — is the backend running?");
            }
            catch (Exception ex)
            {
                Debug.LogWarning($"[gRPC] Server not reachable at {serverAddress}: {ex.Message}");
            }
        }

        private void OnDestroy()
        {
            _cts?.Cancel();
            _cts?.Dispose();
            _channel?.Dispose();
        }
    }
}
