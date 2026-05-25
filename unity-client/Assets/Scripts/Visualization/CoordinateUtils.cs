using Gakkel.Swarm.Contracts.V1;
using UnityEngine;

namespace Gakkel.Swarm.Unity
{
    public static class CoordinateUtils
    {
        /// <summary>Converts NED (North/East/Down) to Unity (East/Up/North) coordinates.</summary>
        public static Vector3 NedToUnity(Vec3 ned) => new(ned.Y, -ned.Z, ned.X);
    }
}
