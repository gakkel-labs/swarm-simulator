using System.Collections.Generic;
using UnityEngine;

namespace Gakkel.Swarm.Unity
{
    internal static class GroupDetector
    {
        // BFS connected-components on a pre-computed position map.
        // Two agents belong to the same group when their distance < groupingRadius.
        // Returns a map of agentId → groupId (ints are arbitrary, stable within one call).
        public static Dictionary<string, int> Compute(
            IReadOnlyDictionary<string, Vector3> positions,
            float groupingRadius)
        {
            var groupId = new Dictionary<string, int>(positions.Count);
            var queue = new Queue<string>();
            int nextGroup = 0;

            foreach (var (id, _) in positions)
            {
                if (groupId.ContainsKey(id)) continue;

                int g = nextGroup++;
                groupId[id] = g;
                queue.Enqueue(id);

                while (queue.Count > 0)
                {
                    var current = queue.Dequeue();
                    foreach (var (otherId, otherPos) in positions)
                    {
                        if (groupId.ContainsKey(otherId)) continue;
                        if (Vector3.Distance(positions[current], otherPos) < groupingRadius)
                        {
                            groupId[otherId] = g;
                            queue.Enqueue(otherId);
                        }
                    }
                }
            }

            return groupId;
        }
    }
}
