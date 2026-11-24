package fr.gakkel.swarmsimulator.swarmserver.domain;

/**
 * The time of one simulation, in seconds since it started. It moves only when the simulation
 * takes a step, never with the computer's clock, so a run gives the same times whether it is
 * shown live or computed as fast as possible.
 *
 * <p>Only the simulation thread calls {@link #advance}. Other threads (the gRPC handlers) may
 * read the time: the field is {@code volatile} so they always see the latest value.
 */
public final class SimulationClock {

    private volatile double elapsedSeconds;

    public double elapsedSeconds() {
        return elapsedSeconds;
    }

    public void advance(double stepSeconds) {
        // "!(x > 0)" also rejects NaN, which fails every comparison.
        if (!(stepSeconds > 0)) {
            throw new IllegalArgumentException("step must be positive, got " + stepSeconds);
        }
        elapsedSeconds += stepSeconds;
    }
}
