using System;

namespace Gakkel.Swarm.Unity
{
    /// <summary>
    /// Detailed or simple view, shared by everything drawn in the scene (V key, see SwarmVisualizer).
    /// Detailed: 3D models (ROV, aircraft wreck, hydrothermal chimneys with smoke), nice for a demo.
    /// Simple: plain shapes (capsules, boxes, columns), cheaper to draw and easier to read for the
    /// operator, who cares about the swarm behaviour more than about the look. See ADR-0006.
    /// </summary>
    public static class DetailedView
    {
        public static bool IsOn { get; private set; } = true;

        /// <summary>Called with the new value each time the view changes.</summary>
        public static event Action<bool> Changed;

        public static void Set(bool isOn)
        {
            if (IsOn == isOn) return;
            IsOn = isOn;
            Changed?.Invoke(isOn);
        }
    }
}
