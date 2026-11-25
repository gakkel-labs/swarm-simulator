package fr.gakkel.swarmsimulator.swarmserver.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * One scenario of the common set: where the wreck lies, or no wreck at all, and the seed that
 * draws everything random in the run. A value object: two scenarios with the same wreck and the
 * same seed are the same scenario, and every fleet configuration runs on the same scenarios.
 *
 * @param wreckPosition where the wreck lies, in the search area, or empty for a scenario with no
 *                      wreck
 * @param seed          the master seed of the run
 */
public record Scenario(Optional<Vector3D> wreckPosition, long seed) {

    public Scenario {
        Objects.requireNonNull(wreckPosition, "wreckPosition");
    }

    public static Scenario withWreckAt(Vector3D wreckPosition, long seed) {
        return new Scenario(Optional.of(wreckPosition), seed);
    }

    public static Scenario withoutWreck(long seed) {
        return new Scenario(Optional.empty(), seed);
    }
}
