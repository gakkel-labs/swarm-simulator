package fr.gakkel.swarmsimulator.swarmserver.domain;

import java.math.BigDecimal;

/**
 * The search area of a mission, seen from above: a rectangle facing North, from its south-west
 * corner. The operator chooses it: it comes from the mission, never from the engine (ADR-0011).
 * Its origin in latitude / longitude and other shapes come with the nautical chart (#182).
 *
 * <p>A value object: no id, never changes, two search areas with the same sizes are the same.
 *
 * @param widthEastMeters   the size towards the East, in metres
 * @param lengthNorthMeters the size towards the North, in metres
 */
public record SearchArea(double widthEastMeters, double lengthNorthMeters) {

    private static final double SQUARE_METERS_PER_SQUARE_KILOMETER = 1_000_000.0;

    public SearchArea {
        requirePositiveAndFinite(widthEastMeters, "width");
        requirePositiveAndFinite(lengthNorthMeters, "length");
    }

    public double areaSquareKilometers() {
        return widthEastMeters * lengthNorthMeters / SQUARE_METERS_PER_SQUARE_KILOMETER;
    }

    /** For logs and messages: "7000 x 5000 m" (East x North). */
    @Override
    public String toString() {
        return meters(widthEastMeters) + " x " + meters(lengthNorthMeters) + " m";
    }

    // 7000.0 is written 7000, 100.5 stays 100.5, and a large size never becomes 1.2E7.
    private static String meters(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    // "!(x > 0)" also rejects NaN, which fails every comparison.
    private static void requirePositiveAndFinite(double meters, String name) {
        if (!(meters > 0) || Double.isInfinite(meters)) {
            throw new IllegalArgumentException(
                    "search area " + name + " must be positive and finite, got " + meters);
        }
    }
}
