package fr.gakkel.swarmsimulator.swarmserver.domain;

import java.util.Objects;

import static fr.gakkel.swarmsimulator.swarmserver.domain.SpecificationChecks.requirePositive;

/**
 * The sensors a drone uses only to move without hitting anything (ADR-0011, section 4). The
 * pressure sensor has no value to set: it always gives the depth of the drone.
 *
 * @param altimeterMaxRangeMeters the largest distance to the seabed the altimeter can measure
 * @param forwardSonar            the obstacle avoidance sonar
 */
public record NavigationSensors(double altimeterMaxRangeMeters, ForwardSonar forwardSonar) {

    public NavigationSensors {
        requirePositive("altimeterMaxRangeMeters", altimeterMaxRangeMeters);
        Objects.requireNonNull(forwardSonar, "forwardSonar");
    }
}
