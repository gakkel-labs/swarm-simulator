package fr.gakkel.swarmsimulator.swarmserver.domain;

import java.util.Objects;

/** A number of drones of one model, in a {@link FleetConfiguration}. A value object. */
public record DroneGroup(DroneSpecification specification, int count) {

    public DroneGroup {
        Objects.requireNonNull(specification, "specification");
        if (count < 1) {
            throw new IllegalArgumentException("drone count of model '" + specification.model()
                    + "' must be >= 1, got " + count);
        }
    }
}
