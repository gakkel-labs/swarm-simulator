package fr.gakkel.swarmsimulator.swarmserver.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The choice of drones for a mission: which drone models, how many of each, and which search
 * pattern they use. A swarm can mix several models (#176). The backend can test many
 * configurations and propose the best one, or the operator can choose one.
 *
 * <p>A value object: it has no id and never changes, two configurations with the same values
 * are the same configuration. The order of the groups is kept: drones are created group by
 * group, so the same configuration and seed always give the same world (ADR-0010).
 */
public record FleetConfiguration(List<DroneGroup> droneGroups,
                                 SearchPatternSettings searchPattern) {

    public FleetConfiguration {
        // A copy: changing the caller's list afterwards cannot change this configuration.
        droneGroups = List.copyOf(Objects.requireNonNull(droneGroups, "droneGroups"));
        if (droneGroups.isEmpty()) {
            throw new IllegalArgumentException("a fleet configuration needs at least one drone");
        }
        Set<String> models = new HashSet<>();
        for (DroneGroup group : droneGroups) {
            String model = group.specification().model();
            if (!models.add(model)) {
                throw new IllegalArgumentException("drone model '" + model
                        + "' is in two groups: give it one group with the total count");
            }
        }
        Objects.requireNonNull(searchPattern, "searchPattern");
    }

    /** A fleet of {@code count} drones of one model. */
    public static FleetConfiguration ofOneModel(DroneSpecification specification, int count,
                                                SearchPatternSettings searchPattern) {
        return new FleetConfiguration(List.of(new DroneGroup(specification, count)),
                searchPattern);
    }

    /** The number of drones of every model together. */
    public int droneCount() {
        return droneGroups.stream().mapToInt(DroneGroup::count).sum();
    }

    /** The cruise speed of the fastest model of the fleet. */
    public double fastestCruiseSpeedMetersPerSecond() {
        return droneGroups.stream()
                .mapToDouble(group -> group.specification().cruiseSpeedMetersPerSecond())
                .max()
                .orElseThrow();
    }

    /** The range of the search sensor that sees the farthest in the fleet. */
    public double longestSearchRangeMeters() {
        return droneGroups.stream()
                .mapToDouble(group -> group.specification().searchSensor().maxRangeMeters())
                .max()
                .orElseThrow();
    }
}
