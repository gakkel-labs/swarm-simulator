package fr.gakkel.swarmsimulator.swarmserver.domain;

import static fr.gakkel.swarmsimulator.swarmserver.domain.SpecificationChecks.requirePositive;

/**
 * The chance that a search sensor detects the wreck, by distance across its swath (ADR-0011):
 * {@code detectionProbability} from the edge of the nadir to {@code fullProbabilityUntilMeters},
 * then a straight drop to 0 at the sensor range. The sensor that holds the curve checks that
 * these distances fit its own range.
 */
public record DetectionCurve(double detectionProbability, double fullProbabilityUntilMeters) {

    public DetectionCurve {
        // 0 is refused too: a sensor that never detects anything is a mistake in the catalog.
        if (!(detectionProbability > 0 && detectionProbability <= 1)) {
            throw new IllegalArgumentException(
                    "detectionProbability must be in (0, 1], got " + detectionProbability);
        }
        requirePositive("fullProbabilityUntilMeters", fullProbabilityUntilMeters);
    }
}
