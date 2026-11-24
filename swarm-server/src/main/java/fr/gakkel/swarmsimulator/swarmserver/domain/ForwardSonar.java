package fr.gakkel.swarmsimulator.swarmserver.domain;

import static fr.gakkel.swarmsimulator.swarmserver.domain.SpecificationChecks.requirePositive;

/** The obstacle avoidance sonar: a cone in front of the drone (ADR-0011, section 4). */
public record ForwardSonar(double rangeMeters, double fieldOfViewDegrees) {

    public ForwardSonar {
        requirePositive("rangeMeters", rangeMeters);
        requirePositive("fieldOfViewDegrees", fieldOfViewDegrees);
        if (fieldOfViewDegrees > 360) {
            throw new IllegalArgumentException(
                    "fieldOfViewDegrees must be <= 360, got " + fieldOfViewDegrees);
        }
    }
}
