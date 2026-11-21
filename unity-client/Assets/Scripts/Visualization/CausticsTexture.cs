using UnityEngine;

namespace Gakkel.Swarm.Unity
{
    /// <summary>
    /// Builds a tileable caustics pattern: the net of bright lines that waves draw on the seabed
    /// when they focus the light. Used as a light cookie on the directional light (a texture that
    /// filters the light like a stencil). Made by code so there is no image to keep in the repo.
    /// The lines are the edges of Voronoi cells: each pixel looks at its two nearest random points,
    /// and is bright when it is almost as close to the second as to the first.
    /// </summary>
    public static class CausticsTexture
    {
        private const float LineWidth = 0.12f;      // in cell sizes
        private const float DarkestLight = 0.55f;   // light between the lines, 1 = full light

        public static Texture2D Create(int sizePixels, int cellsPerSide, int seed)
        {
            var random = new System.Random(seed);
            var cellPoints = new Vector2[cellsPerSide, cellsPerSide];
            for (var cellX = 0; cellX < cellsPerSide; cellX++)
                for (var cellY = 0; cellY < cellsPerSide; cellY++)
                    cellPoints[cellX, cellY] = new Vector2((float)random.NextDouble(), (float)random.NextDouble());

            var texture = new Texture2D(sizePixels, sizePixels, TextureFormat.RGBA32, true)
            {
                name = "Caustics",
                wrapMode = TextureWrapMode.Repeat,
                filterMode = FilterMode.Bilinear,
            };
            var pixels = new Color[sizePixels * sizePixels];
            for (var row = 0; row < sizePixels; row++)
            {
                for (var column = 0; column < sizePixels; column++)
                {
                    var positionInCells = new Vector2(column + 0.5f, row + 0.5f) * cellsPerSide / sizePixels;
                    var lineAmount = LineAmount(positionInCells, cellPoints, cellsPerSide);
                    var light = Mathf.Lerp(DarkestLight, 1f, lineAmount);
                    pixels[row * sizePixels + column] = new Color(light, light, light, 1f);
                }
            }
            texture.SetPixels(pixels);
            texture.Apply(true, true); // mipmaps, then free the CPU copy
            return texture;
        }

        // 1 on a cell edge, 0 inside a cell. The grid wraps around, so the texture tiles without seams.
        private static float LineAmount(Vector2 positionInCells, Vector2[,] cellPoints, int cellsPerSide)
        {
            var cellX = Mathf.FloorToInt(positionInCells.x);
            var cellY = Mathf.FloorToInt(positionInCells.y);
            var nearestDistance = float.MaxValue;
            var secondNearestDistance = float.MaxValue;
            for (var offsetX = -1; offsetX <= 1; offsetX++)
            {
                for (var offsetY = -1; offsetY <= 1; offsetY++)
                {
                    var neighbourX = cellX + offsetX;
                    var neighbourY = cellY + offsetY;
                    var wrappedX = (neighbourX % cellsPerSide + cellsPerSide) % cellsPerSide;
                    var wrappedY = (neighbourY % cellsPerSide + cellsPerSide) % cellsPerSide;
                    var point = new Vector2(neighbourX, neighbourY) + cellPoints[wrappedX, wrappedY];
                    var distance = Vector2.Distance(positionInCells, point);
                    if (distance < nearestDistance)
                    {
                        secondNearestDistance = nearestDistance;
                        nearestDistance = distance;
                    }
                    else if (distance < secondNearestDistance)
                    {
                        secondNearestDistance = distance;
                    }
                }
            }
            var edgeAmount = 1f - Mathf.SmoothStep(0f, LineWidth, secondNearestDistance - nearestDistance);
            return edgeAmount * edgeAmount;
        }
    }
}
