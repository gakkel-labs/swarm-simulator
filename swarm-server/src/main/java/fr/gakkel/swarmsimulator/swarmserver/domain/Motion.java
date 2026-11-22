package fr.gakkel.swarmsimulator.swarmserver.domain;

import java.util.Objects;

/**
 * Position and velocity of a moving thing at one simulation step.
 *
 * <p>The simulation thread writes them and the broadcast thread reads them. Keeping both in one
 * immutable value, swapped in a single write, means a reader never gets a position from one step
 * and a velocity from another.
 */
public record Motion(Vector3D position, Vector3D velocity) {

    public Motion {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(velocity, "velocity");
    }
}
