package fr.gakkel.swarmsimulator.swarmserver.domain;

/**
 * Settings of the predator (the "danger" of the glossary) that hunts the drones.
 *
 * @param speed     how fast it swims toward the nearest drone, in m/s
 * @param eatRadius distance under which it catches a drone, in metres
 */
public record PredatorConfig(double speed, double eatRadius) {

    private static final double DEFAULT_SPEED = 3.0;
    private static final double DEFAULT_EAT_RADIUS = 1.5;

    public PredatorConfig {
        if (speed <= 0) {
            throw new IllegalArgumentException("speed must be positive");
        }
        if (eatRadius <= 0) {
            throw new IllegalArgumentException("eatRadius must be positive");
        }
    }

    public static PredatorConfig defaults() {
        return new PredatorConfig(DEFAULT_SPEED, DEFAULT_EAT_RADIUS);
    }
}
