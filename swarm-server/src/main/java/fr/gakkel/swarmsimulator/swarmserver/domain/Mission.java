package fr.gakkel.swarmsimulator.swarmserver.domain;

import java.util.Objects;

/**
 * What the operator sets for a mission, as far as the simulation needs it in Step 2: where to
 * search, how deep the seabed is, and the loss risk of the area. Every fleet configuration
 * compared runs on the same mission.
 *
 * <p>A value object: no id, never changes. The seabed is flat in Step 2 (ADR-0011): one depth
 * for the whole search area. The real seabed comes with #188.
 *
 * @param searchArea        the search area, chosen by the operator
 * @param seabedDepthMeters the depth of the flat seabed, in metres below the surface
 * @param lossRisk          the risk of losing a drone in this area (ADR-0012)
 */
public record Mission(SearchArea searchArea, double seabedDepthMeters, LossRiskLevel lossRisk) {

    public Mission {
        Objects.requireNonNull(searchArea, "searchArea");
        Objects.requireNonNull(lossRisk, "lossRisk");
        // "!(x > 0)" also rejects NaN, which fails every comparison.
        if (!(seabedDepthMeters > 0) || Double.isInfinite(seabedDepthMeters)) {
            throw new IllegalArgumentException(
                    "seabed depth must be positive and finite, got " + seabedDepthMeters);
        }
    }
}
