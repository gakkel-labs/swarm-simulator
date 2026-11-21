using Gakkel.Swarm.Contracts.V1;
using UnityEngine;

namespace Gakkel.Swarm.Unity
{
    public static class CoordinateUtils
    {
        /// <summary>Converts NED (North/East/Down) to Unity (East/Up/North) coordinates.</summary>
        public static Vector3 NedToUnity(Vec3 ned) => new(ned.Y, -ned.Z, ned.X);

        /// <summary>Converts Unity (East/Up/North) to NED (North/East/Down). Inverse of <see cref="NedToUnity"/>.</summary>
        public static Vec3 UnityToNed(Vector3 unity) => new() { X = unity.z, Y = unity.x, Z = -unity.y };
    }
}
