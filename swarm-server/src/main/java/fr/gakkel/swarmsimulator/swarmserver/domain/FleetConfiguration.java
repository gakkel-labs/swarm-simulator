package fr.gakkel.swarmsimulator.swarmserver.domain;

import java.util.Objects;

/**
 * The choice of drones for a mission: how many detection drones and which search pattern they
 * use. The backend can test many configurations and propose the best one, or the operator can
 * choose one.
 *
 * <p>A value object: it has no id and never changes, two configurations with the same values
 * are the same configuration.
 */
public record FleetConfiguration(int droneCount, SearchPatternSettings searchPattern) {

    public FleetConfiguration {
        if (droneCount < 1) {
            throw new IllegalArgumentException("drone count must be >= 1, got " + droneCount);
        }
        Objects.requireNonNull(searchPattern, "searchPattern");
    }
}
