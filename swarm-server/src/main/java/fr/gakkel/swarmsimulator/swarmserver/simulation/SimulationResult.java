package fr.gakkel.swarmsimulator.swarmserver.simulation;

import fr.gakkel.swarmsimulator.swarmserver.domain.SimulationEvent;
import fr.gakkel.swarmsimulator.swarmserver.domain.SimulationEvent.DroneLost;
import fr.gakkel.swarmsimulator.swarmserver.domain.SimulationEvent.EndReason;
import fr.gakkel.swarmsimulator.swarmserver.domain.SimulationEvent.LossCause;
import fr.gakkel.swarmsimulator.swarmserver.domain.SimulationEvent.RunEnded;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * What one simulation of a mission gives once it has ended: how and when it ended, and every
 * event of the run, in order. The score (#82) is computed from many of them.
 *
 * @param end    how and when the run ended
 * @param events every event of the run, in the order they happened (the last one is {@code end})
 */
public record SimulationResult(RunEnded end, List<SimulationEvent> events) {

    public SimulationResult {
        Objects.requireNonNull(end, "end");
        events = List.copyOf(Objects.requireNonNull(events, "events"));
    }

    /**
     * When the wreck was detected, in seconds of simulation time, or empty if the run did not end
     * on it. A wreck seen in the step that went past the limit time is a failure (ADR-0013), so
     * the end of the run decides, not the {@code TargetDetected} event alone.
     */
    public OptionalDouble wreckDetectedAtSeconds() {
        return end.reason() == EndReason.TARGET_DETECTED
                ? OptionalDouble.of(end.atSeconds())
                : OptionalDouble.empty();
    }

    /** How many drones the loss risk of the mission took during the run (ADR-0012). */
    public long lostDroneCount() {
        return events.stream()
                .filter(event -> event instanceof DroneLost lost
                        && lost.cause() == LossCause.LOSS_RISK)
                .count();
    }
}
