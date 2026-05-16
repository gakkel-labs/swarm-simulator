using System;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;
using Cysharp.Net.Http;
using Grpc.Net.Client;
using UnityEngine;

namespace Gakkel.Swarm.Unity
{
    // Verifies HTTP/2 reachability on serverAddress without opening the gRPC stream.
    // Grpc.Net.Client connects lazily, so we probe via a raw HEAD request instead.
    public class GrpcConnectionTest : MonoBehaviour
    {
        [SerializeField] private string serverAddress = "http://localhost:50051";
        [SerializeField] private float connectTimeoutSeconds = 3f;

        private GrpcChannel _channel;

        private async void Start()
        {
            try
            {
                var handler = new YetAnotherHttpHandler { Http2Only = true };
                _channel = GrpcChannel.ForAddress(serverAddress, new GrpcChannelOptions
                {
                    HttpHandler = handler,
                    DisposeHttpClient = true,
                });

                using var cts = new CancellationTokenSource(
                    TimeSpan.FromSeconds(connectTimeoutSeconds));

                // Probe TCP reachability: a HEAD request will be refused by the gRPC
                // server (405) but proves HTTP/2 connectivity is up.
                using var probeClient = new HttpClient(new YetAnotherHttpHandler { Http2Only = true });
                probeClient.Timeout = TimeSpan.FromSeconds(connectTimeoutSeconds);
                var response = await probeClient.SendAsync(
                    new HttpRequestMessage(HttpMethod.Head, serverAddress), cts.Token);

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
            _channel?.Dispose();
        }
    }
}
