package fr.gakkel.swarmsimulator.swarmserver.simulation;

import fr.gakkel.swarmsimulator.swarmserver.domain.SearchArea;

/**
 * Physical constants of the simulated world that are shared across packages.
 * Kept here (not in SimulationLoop) so server adapters can read them without
 * depending on the loop's internals.
 */
public final class SimulationConstants {

    // Default values of the live demo, read by Settings. The scoring has no default search
    // area and no default seabed depth: the mission must give them (#177). The drone values (speed, sensor range) are
    // not here: they come from the drone catalog (#176).
    /** The search area of the live demo: 100 m to the East, 50 m to the North. */
    public static final SearchArea DEMO_SEARCH_AREA = new SearchArea(100, 50);
    /** The flat seabed of the live demo, in metres below the surface. */
    public static final double DEMO_SEABED_DEPTH = 100;
    public static final int    DEFAULT_AGENT_COUNT  = 20;
    public static final long   DEFAULT_SEED         = 42L;

    private SimulationConstants() {}
}
