using System.Collections.Generic;
using Cysharp.Net.Http;
using Grpc.Net.Client;
using UnityEngine;

namespace Gakkel.Swarm.Unity
{
    /// <summary>
    /// One gRPC channel per server address, shared by every component that talks to the server
    /// (the world state stream and the target placement). A channel is a costly object made to be
    /// reused: it holds the HTTP/2 connection. The channels are closed when the application quits.
    /// </summary>
    public static class SwarmServerChannel
    {
        /// <summary>Default address of the local Java backend (cleartext HTTP/2).</summary>
        public const string DefaultAddress = "http://localhost:50051";

        private static readonly Dictionary<string, GrpcChannel> Channels = new();
        private static bool _closesOnQuit;

        /// <summary>Returns the channel for <paramref name="address"/>, created on first use. Main thread only.</summary>
        public static GrpcChannel For(string address)
        {
            if (Channels.TryGetValue(address, out var channel)) return channel;

            channel = GrpcChannel.ForAddress(address, new GrpcChannelOptions
            {
                HttpHandler = new YetAnotherHttpHandler { Http2Only = true },
                DisposeHttpClient = true,
            });
            Channels[address] = channel;
            if (!_closesOnQuit)
            {
                Application.quitting += CloseAll;
                _closesOnQuit = true;
            }
            return channel;
        }

        private static void CloseAll()
        {
            foreach (var channel in Channels.Values) channel.Dispose();
            Channels.Clear();
            Application.quitting -= CloseAll;
            _closesOnQuit = false;
        }
    }
}
