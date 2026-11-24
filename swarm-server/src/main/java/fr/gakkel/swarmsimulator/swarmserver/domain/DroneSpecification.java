package fr.gakkel.swarmsimulator.swarmserver.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * The technical values of one drone model (the "drone specification" of the glossary,
 * ADR-0011). Each part of the engine reads only what it needs.
 *
 * <p>A value object: it has no id and never changes. Two specifications with the same values
 * are the same specification. The specifications are kept in the {@link DroneCatalog}, not in
 * the code.
 *
 * @param model             the name of the drone model, unique in the catalog
 * @param role              the role of the drone in the fleet
 * @param movement          speeds, turn rate, working altitude and max depth
 * @param endurance         how long the drone can work before it must come back up
 * @param searchSensor      the sensor that finds the wreck
 * @param navigationSensors the sensors used to move without hitting anything
 * @param acousticModem     how the drone hears its neighbors
 */
public record DroneSpecification(
        String model,
        AgentType role,
        MovementLimits movement,
        Duration endurance,
        SearchSensor searchSensor,
        NavigationSensors navigationSensors,
        AcousticModem acousticModem) {

    public DroneSpecification {
        Objects.requireNonNull(model, "model");
        if (model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(movement, "movement");
        Objects.requireNonNull(endurance, "endurance");
        if (endurance.isZero() || endurance.isNegative()) {
            throw new IllegalArgumentException("endurance must be positive, got " + endurance);
        }
        Objects.requireNonNull(searchSensor, "searchSensor");
        Objects.requireNonNull(navigationSensors, "navigationSensors");
        Objects.requireNonNull(acousticModem, "acousticModem");
    }

    /** The speed of the drone while it searches, also its top speed. */
    public double cruiseSpeedMetersPerSecond() {
        return movement.cruiseSpeedMetersPerSecond();
    }
}
