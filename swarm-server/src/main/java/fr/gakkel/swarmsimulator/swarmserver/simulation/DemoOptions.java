package fr.gakkel.swarmsimulator.swarmserver.simulation;

import fr.gakkel.swarmsimulator.swarmserver.domain.PredatorConfig;

import java.util.Objects;
import java.util.Optional;

/**
 * What only the live demo has: the predator that eats drones and the respawn that brings them
 * back. Batch runs use {@link #none()}: they are mission runs, with no demo side effect.
 *
 * @param predator           the predator of the live demo, or empty for none (ADR-0012)
 * @param respawnEatenDrones true to bring an eaten drone back after a delay; false keeps it lost
 */
public record DemoOptions(Optional<PredatorConfig> predator, boolean respawnEatenDrones) {

    public DemoOptions {
        Objects.requireNonNull(predator, "predator");
    }

    /** No demo option: the run of a batch. */
    public static DemoOptions none() {
        return new DemoOptions(Optional.empty(), false);
    }

    /** The live demo: the predator hunts and the drones it eats come back. */
    public static DemoOptions withPredator(PredatorConfig predator) {
        return new DemoOptions(Optional.of(predator), true);
    }
}
