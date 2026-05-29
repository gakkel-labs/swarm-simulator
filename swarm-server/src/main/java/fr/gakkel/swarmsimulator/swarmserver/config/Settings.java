package fr.gakkel.swarmsimulator.swarmserver.config;

import fr.gakkel.swarmsimulator.swarmserver.domain.BoidsConfig;
import fr.gakkel.swarmsimulator.swarmserver.simulation.DiagnosticsConfig;
import fr.gakkel.swarmsimulator.swarmserver.simulation.SimulationConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
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
        double worldWidth,
        double worldHeight,
        double worldDepth,
        long seed,
        BoidsConfig boids,
        DiagnosticsConfig diagnostics) {

    private static final Logger LOG = LoggerFactory.getLogger(Settings.class);

    // Port is server-only; world/agent/seed defaults live in SimulationConstants (single source of
    // truth, also used by SimulationLoop.createDefaultWorld) and are referenced below.
    public static final int DEFAULT_PORT = 50051;

    private static final String PROPERTIES_RESOURCE = "application.properties";

    public Settings {
        // World dimensions are also validated by the World constructor, but agent count has no other
        // guard: 0 (or negative) would silently produce NaN cohesion metrics on an empty swarm.
        if (agentCount < 1) {
            throw new IllegalArgumentException("agent count must be >= 1 (got " + agentCount + ")");
        }
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
        double worldWidth = doubleValue(env, props, "SWARM_WORLD_WIDTH", "swarm.world.width", SimulationConstants.DEFAULT_WORLD_WIDTH);
        double worldHeight = doubleValue(env, props, "SWARM_WORLD_HEIGHT", "swarm.world.height", SimulationConstants.DEFAULT_WORLD_HEIGHT);
        double worldDepth = doubleValue(env, props, "SWARM_WORLD_DEPTH", "swarm.world.depth", SimulationConstants.DEFAULT_WORLD_DEPTH);
        long seed = resolveSeed(env, props);

        BoidsConfig boids = resolveBoids(env, props);
        DiagnosticsConfig diagnostics = resolveDiagnostics(env, props);

        LOG.info("Settings — port={} | agents={} | world={}x{}x{} | seed={}",
                port, agentCount, worldWidth, worldHeight, worldDepth, seed);

        return new Settings(port, agentCount, worldWidth, worldHeight, worldDepth, seed, boids, diagnostics);
    }

    private static BoidsConfig resolveBoids(Map<String, String> env, Properties props) {
        BoidsConfig.Builder b = BoidsConfig.builder();
        applyDouble(env, props, "SWARM_BOIDS_PERCEPTION_RADIUS",        "swarm.boids.perception-radius",        b::perceptionRadius);
        applyDouble(env, props, "SWARM_BOIDS_SEPARATION_WEIGHT",        "swarm.boids.separation-weight",        b::separationWeight);
        applyDouble(env, props, "SWARM_BOIDS_ALIGNMENT_WEIGHT",         "swarm.boids.alignment-weight",         b::alignmentWeight);
        applyDouble(env, props, "SWARM_BOIDS_COHESION_WEIGHT",          "swarm.boids.cohesion-weight",          b::cohesionWeight);
        applyDouble(env, props, "SWARM_BOIDS_WANDER_WEIGHT",            "swarm.boids.wander-weight",            b::wanderWeight);
        applyDouble(env, props, "SWARM_BOIDS_MAX_SPEED",                "swarm.boids.max-speed",                b::maxSpeed);
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

    /**
     * Resolves the master RNG seed. Accepts a number for reproducible runs, {@code random}/{@code none}
     * for a freshly drawn seed (logged so the run can be replayed), or falls back to
     * {@link SimulationConstants#DEFAULT_SEED}.
     */
    private static long resolveSeed(Map<String, String> env, Properties props) {
        Optional<String> raw = raw(env, props, "SWARM_SEED", "swarm.seed");
        if (raw.isEmpty()) {
            LOG.info("Seed: {} (default)", SimulationConstants.DEFAULT_SEED);
            return SimulationConstants.DEFAULT_SEED;
        }
        String value = raw.get();
        if (value.equalsIgnoreCase("random") || value.equalsIgnoreCase("none")) {
            long drawn = new Random().nextLong();
            LOG.info("Seed: {} (random — set SWARM_SEED={} to replay this run)", drawn, drawn);
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

    private static Properties loadClasspathProperties() {
        Properties props = new Properties();
        try (InputStream in = Settings.class.getClassLoader().getResourceAsStream(PROPERTIES_RESOURCE)) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            LOG.warn("Could not read {} — falling back to env vars and defaults: {}", PROPERTIES_RESOURCE, e.getMessage());
        }
        return props;
    }

    // ---- typed resolution helpers (env → props → default), with clear errors on malformed input ----

    private static Optional<String> raw(Map<String, String> env, Properties props, String envKey, String propKey) {
        String fromEnv = env.get(envKey);
        if (fromEnv != null && !fromEnv.isBlank()) return Optional.of(fromEnv.trim());
        String fromProps = props.getProperty(propKey);
        if (fromProps != null && !fromProps.isBlank()) return Optional.of(fromProps.trim());
        return Optional.empty();
    }

    private static int intValue(Map<String, String> env, Properties props, String envKey, String propKey, int def) {
        return raw(env, props, envKey, propKey).map(s -> parseInt(s, envKey)).orElse(def);
    }

    private static double doubleValue(Map<String, String> env, Properties props, String envKey, String propKey, double def) {
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
