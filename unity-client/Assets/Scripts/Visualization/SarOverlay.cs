using TMPro;
using UnityEngine;

namespace Gakkel.Swarm.Unity
{
    public class SarOverlay : MonoBehaviour
    {
        [SerializeField] private TargetRenderer targetRenderer;
        [SerializeField] private TextMeshProUGUI overlayText;

        private void Update()
        {
            if (targetRenderer == null || overlayText == null) return;

            if (!targetRenderer.IsPlaced)
            {
                overlayText.text = string.Empty;
                return;
            }

            if (targetRenderer.IsFound)
            {
                string shortId = targetRenderer.FoundByAgentId.Length >= 8
                    ? targetRenderer.FoundByAgentId[..8]
                    : targetRenderer.FoundByAgentId;
                overlayText.text = $"Found by drone {shortId} in {targetRenderer.FoundAtElapsedS:F1}s";
            }
            else
            {
                overlayText.text = $"Searching... {targetRenderer.ElapsedSimS:F1}s";
            }
        }
    }
}
