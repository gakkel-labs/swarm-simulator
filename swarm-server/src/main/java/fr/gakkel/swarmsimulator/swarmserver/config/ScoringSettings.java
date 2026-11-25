package fr.gakkel.swarmsimulator.swarmserver.config;

import fr.gakkel.swarmsimulator.swarmserver.config.Settings.RequestedDrones;
import fr.gakkel.swarmsimulator.swarmserver.domain.BoidsConfig;
import fr.gakkel.swarmsimulator.swarmserver.domain.DroneCatalog;
import fr.gakkel.swarmsimulator.swarmserver.domain.DroneGroup;
import fr.gakkel.swarmsimulator.swarmserver.domain.FleetConfiguration;
import fr.gakkel.swarmsimulator.swarmserver.domain.Mission;
import fr.gakkel.swarmsimulator.swarmserver.domain.SearchArea;
import fr.gakkel.swarmsimulator.swarmserver.domain.SearchPatternSettings;
import fr.gakkel.swarmsimulator.swarmserver.domain.Vector3D;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * The settings of a scoring: the mission, the fleet configuration scored and how many
 * simulations to run. Read only when a scoring is launched, never at the start of the server.
 *
 * <p>The search area, the seabed depth and the fleet have <strong>no default</strong>: a score
 * computed on values nobody chose would mislead the operator (#177). A missing key stops the
 * scoring with a message that names it. The seed, the loss risk and the Boids weights use the
 * same keys as the server.
 */
public record ScoringSettings(
        Mission mission,
        List<RequestedDrones> requestedFleet,
        BoidsConfig boids,
        int simulationCount,
        String droneCatalogLocation,
        Optional<Vector3D> wreckPosition,
        long seed) {

    /** How many simulations a scoring runs when none is set (the milestone of Step 2). */
    public static final int DEFAULT_SIMULATION_COUNT = 300;

    /** The drone catalog of a scoring when none is set: the values of ADR-0011. */
    public static final String DEFAULT_DRONE_CATALOG = "classpath:drone-catalog-scoring.json";

    public ScoringSettings {
        Objects.requireNonNull(mission, "mission");
        Objects.requireNonNull(boids, "boids");
        Objects.requireNonNull(droneCatalogLocation, "droneCatalogLocation");
        Objects.requireNonNull(wreckPosition, "wreckPosition");
        requestedFleet = List.copyOf(requestedFleet);
        if (requestedFleet.isEmpty()) {
            throw new IllegalArgumentException("a scoring needs a fleet configuration");
        }
        if (simulationCount < 1) {
            throw new IllegalArgumentException(
                    "simulation count must be >= 1, got " + simulationCount);
        }
    }

    /** Loads the settings of a scoring from the environment and {@code application.properties}. */
    public static ScoringSettings load() {
        return load(System.getenv(), Settings.loadClasspathProperties());
    }

    static ScoringSettings load(Map<String, String> env, Properties props) {
        SearchArea searchArea = new SearchArea(
                requiredDouble(env, props, "SWARM_SCORING_SEARCH_AREA_WIDTH",
                        "swarm.scoring.search-area.width"),
                requiredDouble(env, props, "SWARM_SCORING_SEARCH_AREA_LENGTH",
                        "swarm.scoring.search-area.length"));
        double seabedDepth = requiredDouble(env, props, "SWARM_SCORING_SEABED_DEPTH",
                "swarm.scoring.seabed.depth");
        Mission mission = new Mission(searchArea, seabedDepth,
                Settings.resolveLossRisk(env, props));
        List<RequestedDrones> fleet = Settings.parseFleet(
                required(env, props, "SWARM_SCORING_FLEET", "swarm.scoring.fleet"),
                "SWARM_SCORING_FLEET / swarm.scoring.fleet");
        int simulationCount = Settings.intValue(env, props, "SWARM_SCORING_SIMULATION_COUNT",
                "swarm.scoring.simulation-count", DEFAULT_SIMULATION_COUNT);
        String droneCatalog = Settings.raw(env, props, "SWARM_SCORING_DRONE_CATALOG",
                "swarm.scoring.drone-catalog").orElse(DEFAULT_DRONE_CATALOG);
        Optional<Vector3D> wreckPosition = Settings.raw(env, props, "SWARM_SCORING_WRECK",
                "swarm.scoring.wreck").map(value -> parseWreck(value, seabedDepth));
        return new ScoringSettings(mission, fleet, Settings.resolveBoids(env, props),
                simulationCount, droneCatalog, wreckPosition, Settings.resolveSeed(env, props));
    }

    /**
     * The fleet configuration scored, flocking with Boids (the only search pattern for now).
     *
     * @throws fr.gakkel.swarmsimulator.swarmserver.domain.UnknownDroneModelException when the
     *         fleet names a model missing from {@code catalog}
     */
    public FleetConfiguration fleetConfiguration(DroneCatalog catalog) {
        List<DroneGroup> groups = new ArrayList<>();
        for (RequestedDrones requested : requestedFleet) {
            groups.add(new DroneGroup(catalog.specification(requested.model()), requested.count()));
        }
        return new FleetConfiguration(groups, new SearchPatternSettings.Boids(boids));
    }

    private static String required(Map<String, String> env, Properties props, String envKey,
                                   String propKey) {
        return Settings.raw(env, props, envKey, propKey).orElseThrow(() ->
                new IllegalArgumentException("a scoring needs " + envKey + " (or " + propKey
                        + " in application.properties): there is no default value"));
    }

    private static double requiredDouble(Map<String, String> env, Properties props, String envKey,
                                         String propKey) {
        required(env, props, envKey, propKey);
        return Settings.doubleValue(env, props, envKey, propKey, Double.NaN);
    }

    /**
     * Reads {@code east,north} in metres from the south-west corner. The wreck lies on the flat
     * seabed. Backend frame (ADR-0007): X = East, Y = Up (underwater Y < 0), Z = North.
     */
    private static Vector3D parseWreck(String value, double seabedDepth) {
        String[] eastAndNorth = value.split(",");
        String format = "SWARM_SCORING_WRECK / swarm.scoring.wreck='" + value
                + "' must be east,north in metres";
        if (eastAndNorth.length != 2) {
            throw new IllegalArgumentException(format);
        }
        try {
            double east = Double.parseDouble(eastAndNorth[0].trim());
            double north = Double.parseDouble(eastAndNorth[1].trim());
            return new Vector3D(east, -seabedDepth, north);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(format, exception);
        }
    }
}
