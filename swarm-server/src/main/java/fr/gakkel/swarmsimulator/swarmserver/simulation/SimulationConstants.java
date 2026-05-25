package fr.gakkel.swarmsimulator.swarmserver.simulation;

/**
 * Physical constants of the simulated world that are shared across packages.
 * Kept here (not in SimulationLoop) so server adapters can read them without
 * depending on the loop's internals.
 */
public final class SimulationConstants {

    /** Radius within which an agent detects the target, in meters. */
    public static final double SENSOR_RADIUS_M = 10.0;

    private SimulationConstants() {}
}
