package fr.gakkel.swarmsimulator.swarmserver.simulation;

import java.util.Objects;

/**
 * How one simulation is run: its step and its pace. A profile belongs to a simulation, not to the
 * server: one server runs the live demo and scoring simulations side by side, with the same
 * engine and the same rules (ADR-0011).
 *
 * <p>Results depend on the step, so every fleet configuration compared uses the same profile.
 *
 * @param name        a short name, for logs
 * @param stepSeconds the simulation time of one step
 * @param realTime    true when the steps follow the computer's clock (live demo), false when they
 *                    run as fast as possible (scoring)
 */
public record SimulationProfile(String name, double stepSeconds, boolean realTime) {

    /** The live demo shown in Unity: a small search area, 30 steps per second of real time. */
    public static final SimulationProfile DEMO =
            new SimulationProfile("demo", 1.0 / SimulationLoop.TICK_RATE_HZ, true);

    /**
     * The simulations of a scoring: steps of 1 s, as fast as possible, no display. At 1.5 m/s a
     * drone moves 1.5 m per step, much less than the sonar band, so a wreck is never jumped over.
     */
    public static final SimulationProfile SCORING = new SimulationProfile("scoring", 1.0, false);

    public SimulationProfile {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("a simulation profile needs a name");
        }
        // "!(x > 0)" also rejects NaN, which fails every comparison.
        if (!(stepSeconds > 0) || Double.isInfinite(stepSeconds)) {
            throw new IllegalArgumentException(
                    "step must be positive and finite, got " + stepSeconds);
        }
    }
}
