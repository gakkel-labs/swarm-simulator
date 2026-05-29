package fr.gakkel.swarmsimulator.swarmserver.simulation;

/**
 * Physical constants of the simulated world that are shared across packages.
 * Kept here (not in SimulationLoop) so server adapters can read them without
 * depending on the loop's internals.
 */
public final class SimulationConstants {

    /** Radius within which an agent detects the target, in meters. */
    public static final double SENSOR_RADIUS_M = 10.0;

    // Default scenario parameters — the single source of truth shared by Settings (the production
    // config path) and SimulationLoop.createDefaultWorld (the test / standalone path). Keeping them
    // here avoids two independent literals that could silently drift apart.
    public static final double DEFAULT_WORLD_WIDTH  = 100;
    public static final double DEFAULT_WORLD_HEIGHT = 100;
    public static final double DEFAULT_WORLD_DEPTH  = 50;
    public static final int    DEFAULT_AGENT_COUNT  = 20;
    public static final long   DEFAULT_SEED         = 42L;

    private SimulationConstants() {}
}
