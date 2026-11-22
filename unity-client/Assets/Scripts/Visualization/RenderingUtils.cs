using UnityEngine;

namespace Gakkel.Swarm.Unity
{
    /// <summary>Small drawing helpers shared by the renderers.</summary>
    internal static class RenderingUtils
    {
        // URP/Lit needs explicit setup to render transparent: surface type, blend modes, ZWrite off,
        // keyword toggle, transparent render queue. _BaseColor is the URP-native property (color falls
        // back to it via name aliasing but we set both for safety).
        public static Material MakeTransparentMaterial(Shader litShader, Color color)
        {
            var material = new Material(litShader);
            material.SetOverrideTag("RenderType", "Transparent");
            material.SetFloat("_Surface", 1f);
            material.SetFloat("_Blend", 0f);
            material.SetFloat("_SrcBlend", (float)UnityEngine.Rendering.BlendMode.SrcAlpha);
            material.SetFloat("_DstBlend", (float)UnityEngine.Rendering.BlendMode.OneMinusSrcAlpha);
            material.SetFloat("_ZWrite", 0f);
            material.EnableKeyword("_SURFACE_TYPE_TRANSPARENT");
            material.DisableKeyword("_ALPHATEST_ON");
            material.DisableKeyword("_ALPHAMODULATE_ON");
            material.renderQueue = (int)UnityEngine.Rendering.RenderQueue.Transparent;
            material.SetColor("_BaseColor", color);
            material.color = color;
            return material;
        }
    }

    /// <summary>Drone ids are UUIDs: the HUD and the object names show only their first characters.</summary>
    internal static class DroneIdText
    {
        private const int ShortLength = 8;

        public static string Short(string droneId)
        {
            if (string.IsNullOrEmpty(droneId)) return "-";
            return droneId.Length > ShortLength ? droneId[..ShortLength] : droneId;
        }
    }
}
