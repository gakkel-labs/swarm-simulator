package fr.gakkel.swarmsimulator.swarmserver.simulation;

/**
 * Physical constants of the simulated world that are shared across packages.
 * Kept here (not in SimulationLoop) so server adapters can read them without
 * depending on the loop's internals.
 */
public final class SimulationConstants {

    // Default scenario parameters, read by Settings. The drone values (speed, sensor range) are
    // not here: they come from the drone catalog (#176).
    public static final double DEFAULT_WORLD_WIDTH  = 100;
    public static final double DEFAULT_WORLD_HEIGHT = 100;
    public static final double DEFAULT_WORLD_DEPTH  = 50;
    public static final int    DEFAULT_AGENT_COUNT  = 20;
    public static final long   DEFAULT_SEED         = 42L;

    private SimulationConstants() {}
}
