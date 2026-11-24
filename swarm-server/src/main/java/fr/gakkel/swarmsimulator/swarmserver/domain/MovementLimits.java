package fr.gakkel.swarmsimulator.swarmserver.domain;

import static fr.gakkel.swarmsimulator.swarmserver.domain.SpecificationChecks.requirePositive;

/**
 * How a drone can move (ADR-0011). Part of its {@link DroneSpecification}.
 *
 * @param cruiseSpeedMetersPerSecond   the speed of the drone while it searches, also its top speed
 * @param maxTurnRateDegreesPerSecond  how fast it can turn
 * @param verticalSpeedMetersPerSecond how fast it dives and comes back up
 * @param workingAltitudeMeters        the height above the seabed where its search sensor works
 * @param maxDepthMeters               the deepest it can go
 */
public record MovementLimits(
        double cruiseSpeedMetersPerSecond,
        double maxTurnRateDegreesPerSecond,
        double verticalSpeedMetersPerSecond,
        double workingAltitudeMeters,
        double maxDepthMeters) {

    public MovementLimits {
        requirePositive("cruiseSpeedMetersPerSecond", cruiseSpeedMetersPerSecond);
        requirePositive("maxTurnRateDegreesPerSecond", maxTurnRateDegreesPerSecond);
        requirePositive("verticalSpeedMetersPerSecond", verticalSpeedMetersPerSecond);
        requirePositive("workingAltitudeMeters", workingAltitudeMeters);
        requirePositive("maxDepthMeters", maxDepthMeters);
    }
}
