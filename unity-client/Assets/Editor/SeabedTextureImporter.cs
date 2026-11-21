using UnityEditor;

namespace Gakkel.Swarm.Unity.Editor
{
    /// <summary>
    /// Sets the import settings of the seabed textures automatically (Editor only):
    /// *_normal.png → Normal map; *_mask.png → linear (no sRGB), because a mask map stores data,
    /// not colours (R metallic, G occlusion, B height, A smoothness).
    /// If a texture was imported before this script existed: right-click › Reimport.
    /// </summary>
    public class SeabedTextureImporter : AssetPostprocessor
    {
        private const string SeabedTextureFolder = "Assets/Resources/Seabed/Textures/";

        private void OnPreprocessTexture()
        {
            if (!assetPath.StartsWith(SeabedTextureFolder)) return;
            var textureImporter = (TextureImporter)assetImporter;

            if (assetPath.EndsWith("_normal.png"))
                textureImporter.textureType = TextureImporterType.NormalMap;
            else if (assetPath.EndsWith("_mask.png"))
                textureImporter.sRGBTexture = false;
        }
    }
}
