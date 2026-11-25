package fr.gakkel.swarmsimulator.swarmserver.config;

import fr.gakkel.swarmsimulator.swarmserver.domain.BoidsConfig;
import fr.gakkel.swarmsimulator.swarmserver.domain.DroneCatalog;
import fr.gakkel.swarmsimulator.swarmserver.domain.DroneGroup;
import fr.gakkel.swarmsimulator.swarmserver.domain.FleetConfiguration;
import fr.gakkel.swarmsimulator.swarmserver.domain.LossRiskLevel;
import fr.gakkel.swarmsimulator.swarmserver.domain.Mission;
import fr.gakkel.swarmsimulator.swarmserver.domain.PredatorConfig;
import fr.gakkel.swarmsimulator.swarmserver.domain.SearchArea;
import fr.gakkel.swarmsimulator.swarmserver.domain.SearchPatternSettings;
import fr.gakkel.swarmsimulator.swarmserver.simulation.DemoOptions;
import fr.gakkel.swarmsimulator.swarmserver.simulation.DiagnosticsConfig;
import fr.gakkel.swarmsimulator.swarmserver.simulation.SimulationConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/**
 * Read-once runtime configuration. Resolves every parameter with the precedence
 * <strong>environment variable → {@code application.properties} → hard-coded default</strong>,
 * so the server can be tuned for Docker / experimentation without recompilation (see issue #62).
 *
 * <p>Defaults reproduce the previous hard-coded behaviour exactly: calling {@link #load()} with no
 * environment variables and no property file yields the same simulation as before this change.
 *
 * <p>The Boids and diagnostics defaults are <em>not</em> duplicated here — unset values simply leave
 * the corresponding {@link BoidsConfig.Builder}/{@link DiagnosticsConfig.Builder} default untouched,
 * keeping the builders the single source of truth.
 */
public record Settings(
        int port,
        int agentCount,
        SearchArea demoSearchArea,
        double demoSeabedDepthMeters,
        long seed,
        BoidsConfig boids,
        DiagnosticsConfig diagnostics,
        PredatorConfig predator,
        boolean predatorEnabled,
        LossRiskLevel lossRisk,
        String droneCatalogLocation,
        List<RequestedDrones> requestedFleet) {

    /**
     * A drone model and a count, as written in {@code swarm.fleet}. The model is only a name
     * here: {@link #fleetConfiguration(DroneCatalog)} finds it in the drone catalog.
     */
    public record RequestedDrones(String model, int count) {}

    private static final Logger LOG = LoggerFactory.getLogger(Settings.class);

    // Port is server-only; world/agent/seed defaults live in SimulationConstants (single source of
    // truth, also used by WorldFactory.createDefaultWorld) and are referenced below.
    public static final int DEFAULT_PORT = 50051;

    /** The drone catalog read when none is set: the demo values, inside the jar. */
    public static final String DEFAULT_DRONE_CATALOG = "classpath:drone-catalog-demo.json";

    private static final String PROPERTIES_RESOURCE = "application.properties";
    private static final String FLEET_FORMAT = "model:count,model:count (for example "
            + "side-scan-drone:6,fast-drone:4)";

    public Settings {
        // The search area checks its own sizes, but agent count has no other
        // guard: 0 (or negative) would silently produce NaN cohesion metrics on an empty swarm.
        if (agentCount < 1) {
            throw new IllegalArgumentException("agent count must be >= 1 (got " + agentCount + ")");
        }
        Objects.requireNonNull(predator, "predator");
        Objects.requireNonNull(lossRisk, "lossRisk");
        // A Mission checks the search area and the seabed depth: a bad value stops the start
        // here, before the drone catalog is read.
        new Mission(demoSearchArea, demoSeabedDepthMeters, lossRisk);
        requestedFleet = List.copyOf(requestedFleet);
    }

    /**
     * The fleet configuration of the live simulation, flocking with Boids (the only search
     * pattern for now). With {@code swarm.fleet} set, its models and counts; without it,
     * {@code swarm.agent-count} drones of the first model of the catalog, as before #176.
     *
     * @throws fr.gakkel.swarmsimulator.swarmserver.domain.UnknownDroneModelException when
     *         {@code swarm.fleet} names a model missing from {@code catalog}
     */
    public FleetConfiguration fleetConfiguration(DroneCatalog catalog) {
        SearchPatternSettings searchPattern = new SearchPatternSettings.Boids(boids);
        if (requestedFleet.isEmpty()) {
            return FleetConfiguration.ofOneModel(catalog.defaultSpecification(), agentCount,
                    searchPattern);
        }
        List<DroneGroup> groups = new ArrayList<>();
        for (RequestedDrones requested : requestedFleet) {
            groups.add(new DroneGroup(catalog.specification(requested.model()), requested.count()));
        }
        return new FleetConfiguration(groups, searchPattern);
    }

    /**
     * The mission of the live demo: its small search area, its flat seabed and the loss risk.
     * Its numbers are never used for a score (ADR-0011).
     */
    public Mission demoMission() {
        return new Mission(demoSearchArea, demoSeabedDepthMeters, lossRisk);
    }

    /**
     * The options of the live demo: the predator and the respawn of the drones it eats, or no
     * option when the predator is switched off. They are only for the demo: scoring simulations use
     * {@link DemoOptions#none()} (ADR-0012).
     */
    public DemoOptions demoOptions() {
        return predatorEnabled ? DemoOptions.withPredator(predator) : DemoOptions.withoutPredator();
    }

    /** Loads settings from the process environment and the {@code application.properties} classpath resource. */
    public static Settings load() {
        return load(System.getenv(), loadClasspathProperties());
    }

    /**
     * Core resolver, exposed package-internally for testing without mutating the real process
     * environment. Precedence is env → props → default for every field.
     */
    static Settings load(Map<String, String> env, Properties props) {
        int port = intValue(env, props, "SWARM_PORT", "swarm.port", DEFAULT_PORT);
        int agentCount = intValue(env, props, "SWARM_AGENT_COUNT", "swarm.agent-count", SimulationConstants.DEFAULT_AGENT_COUNT);
        refuseRenamedWorldKeys(env, props);
        SearchArea demoSearchArea = new SearchArea(
                doubleValue(env, props, "SWARM_DEMO_SEARCH_AREA_WIDTH",
                        "swarm.demo.search-area.width",
                        SimulationConstants.DEMO_SEARCH_AREA.widthEastMeters()),
                doubleValue(env, props, "SWARM_DEMO_SEARCH_AREA_LENGTH",
                        "swarm.demo.search-area.length",
                        SimulationConstants.DEMO_SEARCH_AREA.lengthNorthMeters()));
        double demoSeabedDepth = doubleValue(env, props, "SWARM_DEMO_SEABED_DEPTH",
                "swarm.demo.seabed.depth", SimulationConstants.DEMO_SEABED_DEPTH);
        long seed = resolveSeed(env, props);

        BoidsConfig boids = resolveBoids(env, props);
        DiagnosticsConfig diagnostics = resolveDiagnostics(env, props);
        boolean predatorEnabled = booleanValue(env, props, "SWARM_PREDATOR_ENABLED",
                "swarm.predator.enabled", true);
        // A switched-off predator must not stop the server because of its other settings.
        PredatorConfig predator = predatorEnabled
                ? resolvePredator(env, props)
                : PredatorConfig.defaults();
        LossRiskLevel lossRisk = resolveLossRisk(env, props);
        String droneCatalogLocation = raw(env, props, "SWARM_DRONE_CATALOG", "swarm.drone-catalog")
                .orElse(DEFAULT_DRONE_CATALOG);
        List<RequestedDrones> requestedFleet = raw(env, props, "SWARM_FLEET", "swarm.fleet")
                .map(value -> parseFleet(value, "SWARM_FLEET"))
                .orElse(List.of());

        LOG.info("Settings - port={} | agents={} | fleet={} | drone catalog={} | demo search area={} | demo seabed={} m "
                        + "| seed={} | loss risk={} | demo predator={}", port, agentCount,
                requestedFleet.isEmpty() ? "default model" : requestedFleet, droneCatalogLocation,
                demoSearchArea, demoSeabedDepth, seed, lossRisk,
                predatorEnabled ? "on" : "off");

        return new Settings(port, agentCount, demoSearchArea, demoSeabedDepth, seed,
                boids, diagnostics, predator, predatorEnabled, lossRisk, droneCatalogLocation,
                requestedFleet);
    }

    /**
     * The world keys were renamed in #177. A renamed key left in a file must not be ignored
     * without a word: the demo would not be the one asked for.
     */
    private static void refuseRenamedWorldKeys(Map<String, String> env, Properties props) {
        String[][] renamedKeys = {
            {"SWARM_WORLD_WIDTH", "swarm.world.width", "SWARM_DEMO_SEARCH_AREA_WIDTH",
                "swarm.demo.search-area.width"},
            {"SWARM_WORLD_DEPTH", "swarm.world.depth", "SWARM_DEMO_SEARCH_AREA_LENGTH",
                "swarm.demo.search-area.length"},
            {"SWARM_WORLD_HEIGHT", "swarm.world.height", "SWARM_DEMO_SEABED_DEPTH",
                "swarm.demo.seabed.depth"},
        };
        for (String[] keys : renamedKeys) {
            if (raw(env, props, keys[0], keys[1]).isPresent()) {
                throw new IllegalArgumentException(keys[0] + " / " + keys[1] + " was renamed: use "
                        + keys[2] + " / " + keys[3] + " (#177)");
            }
        }
    }

    /**
     * Reads {@code model:count,model:count}; spaces around the parts are allowed.
     * {@code key} names the setting in the error messages.
     */
    static List<RequestedDrones> parseFleet(String value, String key) {
        List<RequestedDrones> fleet = new ArrayList<>();
        for (String part : value.split(",")) {
            String[] modelAndCount = part.trim().split(":");
            if (modelAndCount.length != 2 || modelAndCount[0].isBlank()) {
                throw new IllegalArgumentException(key + "='" + value + "': each part must be "
                        + FLEET_FORMAT);
            }
            String count = modelAndCount[1].trim();
            int parsedCount;
            try {
                parsedCount = Integer.parseInt(count);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(key + "='" + value + "': count '" + count
                        + "' is not a whole number; expected " + FLEET_FORMAT);
            }
            if (parsedCount < 1) {
                throw new IllegalArgumentException(key + "='" + value + "': count of '"
                        + modelAndCount[0].trim() + "' must be >= 1, got " + parsedCount);
            }
            fleet.add(new RequestedDrones(modelAndCount[0].trim(), parsedCount));
        }
        return fleet;
    }

    static BoidsConfig resolveBoids(Map<String, String> env, Properties props) {
        BoidsConfig.Builder b = BoidsConfig.builder();
        applyDouble(env, props, "SWARM_BOIDS_PERCEPTION_RADIUS",        "swarm.boids.perception-radius",        b::perceptionRadius);
        applyDouble(env, props, "SWARM_BOIDS_SEPARATION_WEIGHT",        "swarm.boids.separation-weight",        b::separationWeight);
        applyDouble(env, props, "SWARM_BOIDS_ALIGNMENT_WEIGHT",         "swarm.boids.alignment-weight",         b::alignmentWeight);
        applyDouble(env, props, "SWARM_BOIDS_COHESION_WEIGHT",          "swarm.boids.cohesion-weight",          b::cohesionWeight);
        applyDouble(env, props, "SWARM_BOIDS_WANDER_WEIGHT",            "swarm.boids.wander-weight",            b::wanderWeight);
        applyDouble(env, props, "SWARM_BOIDS_BOUNDARY_REPULSION_RADIUS", "swarm.boids.boundary-repulsion-radius", b::boundaryRepulsionRadius);
        applyDouble(env, props, "SWARM_BOIDS_BOUNDARY_REPULSION_WEIGHT", "swarm.boids.boundary-repulsion-weight", b::boundaryRepulsionWeight);
        applyDouble(env, props, "SWARM_BOIDS_OBSTACLE_AVOIDANCE_RADIUS", "swarm.boids.obstacle-avoidance-radius", b::obstacleAvoidanceRadius);
        applyDouble(env, props, "SWARM_BOIDS_OBSTACLE_AVOIDANCE_WEIGHT", "swarm.boids.obstacle-avoidance-weight", b::obstacleAvoidanceWeight);
        applyDouble(env, props, "SWARM_BOIDS_THREAT_FLEE_RADIUS",       "swarm.boids.threat-flee-radius",       b::threatFleeRadius);
        applyDouble(env, props, "SWARM_BOIDS_THREAT_FLEE_WEIGHT",       "swarm.boids.threat-flee-weight",       b::threatFleeWeight);
        return b.build();
    }

    private static DiagnosticsConfig resolveDiagnostics(Map<String, String> env, Properties props) {
        DiagnosticsConfig.Builder b = DiagnosticsConfig.builder();
        applyDouble(env, props, "SWARM_DIAG_IMMOBILE_THRESHOLD_FRACTION", "swarm.diag.immobile-threshold-fraction", b::immobileThresholdFraction);
        applyDouble(env, props, "SWARM_DIAG_FROZEN_THRESHOLD_FRACTION",   "swarm.diag.frozen-threshold-fraction",   b::frozenThresholdFraction);
        applyDouble(env, props, "SWARM_DIAG_DISPERSAL_LIMIT_FRACTION",    "swarm.diag.dispersal-limit-fraction",    b::dispersalLimitFraction);
        applyDouble(env, props, "SWARM_DIAG_FLOCKING_LOST_FRACTION",      "swarm.diag.flocking-lost-fraction",      b::flockingLostFraction);
        applyDouble(env, props, "SWARM_DIAG_STABILITY_TOLERANCE",         "swarm.diag.stability-tolerance",         b::stabilityTolerance);
        applyInt(env, props,    "SWARM_DIAG_STABILITY_SAMPLES",           "swarm.diag.stability-samples",           b::stabilitySamples);
        return b.build();
    }

    private static PredatorConfig resolvePredator(Map<String, String> env, Properties props) {
        PredatorConfig defaults = PredatorConfig.defaults();
        double speed = doubleValue(env, props, "SWARM_PREDATOR_SPEED", "swarm.predator.speed",
                defaults.speed());
        double eatRadius = doubleValue(env, props, "SWARM_PREDATOR_EAT_RADIUS",
                "swarm.predator.eat-radius", defaults.eatRadius());
        try {
            return new PredatorConfig(speed, eatRadius);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("SWARM_PREDATOR_SPEED and SWARM_PREDATOR_EAT_RADIUS "
                    + "must be positive: " + exception.getMessage(), exception);
        }
    }

    static LossRiskLevel resolveLossRisk(Map<String, String> env, Properties props) {
        Optional<String> raw = raw(env, props, "SWARM_LOSS_RISK_LEVEL", "swarm.loss-risk.level");
        if (raw.isEmpty()) {
            return LossRiskLevel.NONE;
        }
        try {
            return LossRiskLevel.fromSetting(raw.get());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("SWARM_LOSS_RISK_LEVEL: " + exception.getMessage(),
                    exception);
        }
    }

    /**
     * Resolves the master RNG seed. Accepts a number for reproducible runs, {@code random}/{@code none}
     * for a freshly drawn seed (logged so the run can be replayed), or falls back to
     * {@link SimulationConstants#DEFAULT_SEED}.
     */
    static long resolveSeed(Map<String, String> env, Properties props) {
        Optional<String> raw = raw(env, props, "SWARM_SEED", "swarm.seed");
        if (raw.isEmpty()) {
            LOG.info("Seed: {} (default)", SimulationConstants.DEFAULT_SEED);
            return SimulationConstants.DEFAULT_SEED;
        }
        String value = raw.get();
        if (value.equalsIgnoreCase("random") || value.equalsIgnoreCase("none")) {
            long drawn = new Random().nextLong();
            LOG.info("Seed: {} (random - set SWARM_SEED={} to replay this run)", drawn, drawn);
            return drawn;
        }
        try {
            long parsed = Long.parseLong(value);
            LOG.info("Seed: {} (configured)", parsed);
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "SWARM_SEED='" + value + "' must be an integer, 'random', or 'none'");
        }
    }

    static Properties loadClasspathProperties() {
        Properties props = new Properties();
        try (InputStream in = Settings.class.getClassLoader().getResourceAsStream(PROPERTIES_RESOURCE)) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            LOG.warn("Could not read {} - falling back to env vars and defaults: {}", PROPERTIES_RESOURCE, e.getMessage());
        }
        return props;
    }

    // ---- typed resolution helpers (env → props → default), with clear errors on malformed input ----

    static Optional<String> raw(Map<String, String> env, Properties props, String envKey, String propKey) {
        String fromEnv = env.get(envKey);
        if (fromEnv != null && !fromEnv.isBlank()) return Optional.of(fromEnv.trim());
        String fromProps = props.getProperty(propKey);
        if (fromProps != null && !fromProps.isBlank()) return Optional.of(fromProps.trim());
        return Optional.empty();
    }

    // Only "true" or "false": a typo such as "ture" must not silently switch a feature off.
    private static boolean booleanValue(Map<String, String> env, Properties props, String envKey,
                                        String propKey, boolean def) {
        return raw(env, props, envKey, propKey).map(value -> {
            if (value.equalsIgnoreCase("true")) {
                return true;
            }
            if (value.equalsIgnoreCase("false")) {
                return false;
            }
            throw new IllegalArgumentException(envKey + "='" + value + "' must be true or false");
        }).orElse(def);
    }

    static int intValue(Map<String, String> env, Properties props, String envKey, String propKey, int def) {
        return raw(env, props, envKey, propKey).map(s -> parseInt(s, envKey)).orElse(def);
    }

    static double doubleValue(Map<String, String> env, Properties props, String envKey, String propKey, double def) {
        return raw(env, props, envKey, propKey).map(s -> parseDouble(s, envKey)).orElse(def);
    }

    private static void applyInt(Map<String, String> env, Properties props, String envKey, String propKey, IntConsumer setter) {
        raw(env, props, envKey, propKey).ifPresent(s -> setter.accept(parseInt(s, envKey)));
    }

    private static void applyDouble(Map<String, String> env, Properties props, String envKey, String propKey, DoubleConsumer setter) {
        raw(env, props, envKey, propKey).ifPresent(s -> setter.accept(parseDouble(s, envKey)));
    }

    private static int parseInt(String value, String key) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + "='" + value + "' is not a valid integer");
        }
    }

    private static double parseDouble(String value, String key) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + "='" + value + "' is not a valid number");
        }
    }
}
