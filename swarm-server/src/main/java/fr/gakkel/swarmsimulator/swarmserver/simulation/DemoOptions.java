package fr.gakkel.swarmsimulator.swarmserver.simulation;

import fr.gakkel.swarmsimulator.swarmserver.domain.PredatorConfig;

import java.util.Objects;
import java.util.Optional;

/**
 * What only the live demo has: the predator that eats drones, the respawn that brings them
 * back, and three obstacles to show how the swarm goes around them. Scoring simulations use
 * {@link #none()}: they are mission runs, with no demo side effect, over an open seabed (Step 2).
 *
 * @param predator           the predator of the live demo, or empty for none (ADR-0012)
 * @param respawnEatenDrones true to bring an eaten drone back after a delay; false keeps it lost
 * @param obstacles          true to place the three obstacles of the live demo
 */
public record DemoOptions(Optional<PredatorConfig> predator, boolean respawnEatenDrones,
                          boolean obstacles) {

    public DemoOptions {
        Objects.requireNonNull(predator, "predator");
    }

    /** No demo option: a simulation of a scoring. */
    public static DemoOptions none() {
        return new DemoOptions(Optional.empty(), false, false);
    }

    /** The live demo: the predator hunts, the drones it eats come back, obstacles are placed. */
    public static DemoOptions withPredator(PredatorConfig predator) {
        return new DemoOptions(Optional.of(predator), true, true);
    }

    /** The live demo with the predator switched off: only the obstacles. */
    public static DemoOptions withoutPredator() {
        return new DemoOptions(Optional.empty(), false, true);
    }
}
