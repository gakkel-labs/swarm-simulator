package fr.gakkel.swarmsimulator.swarmserver.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Something that happened during a run, at a time of simulation (a domain event). The engine
 * only says what happened; the live loop logs it, the score (#82) counts it, the replay will
 * store it. The engine does not know who listens.
 *
 * <p>The list is sealed: a new event shows every {@code switch} that must handle it.
 */
public sealed interface SimulationEvent {

    /** The simulation time of the event, in seconds since the start of the run. */
    double atSeconds();

    /** Why a drone left the search area for good, or until it respawns. */
    enum LossCause {
        /** Lost to the loss risk of the mission (ADR-0012). It never comes back. */
        LOSS_RISK,
        /** Eaten by the predator of the live demo. It may respawn. */
        DEMO_PREDATOR
    }

    /** Why a run ended. */
    enum EndReason {
        TARGET_DETECTED,
        LIMIT_TIME_REACHED,
        NO_DRONE_LEFT
    }

    /** A drone passed close enough to the target to see it. */
    record TargetDetected(UUID droneId, double atSeconds) implements SimulationEvent {

        public TargetDetected {
            Objects.requireNonNull(droneId, "droneId");
        }
    }

    /**
     * A drone left the world. With the cause {@link LossCause#DEMO_PREDATOR} it may come back
     * later ({@link DroneRespawned}): count the losses for good with
     * {@code Simulation.lostDroneIds()}. Scoring simulations have no predator.
     */
    record DroneLost(UUID droneId, LossCause cause, double atSeconds) implements SimulationEvent {

        public DroneLost {
            Objects.requireNonNull(droneId, "droneId");
            Objects.requireNonNull(cause, "cause");
        }
    }

    /** A drone eaten by the demo predator came back into the search area. */
    record DroneRespawned(UUID droneId, double atSeconds) implements SimulationEvent {

        public DroneRespawned {
            Objects.requireNonNull(droneId, "droneId");
        }
    }

    /** The run is over. It is always the last event of a run. */
    record RunEnded(EndReason reason, double atSeconds) implements SimulationEvent {

        public RunEnded {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
