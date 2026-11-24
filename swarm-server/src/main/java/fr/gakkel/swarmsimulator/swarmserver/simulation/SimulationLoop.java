package fr.gakkel.swarmsimulator.swarmserver.simulation;

import fr.gakkel.swarmsimulator.swarmserver.domain.Agent;
import fr.gakkel.swarmsimulator.swarmserver.domain.AgentType;
import fr.gakkel.swarmsimulator.swarmserver.domain.BoidsConfig;
import fr.gakkel.swarmsimulator.swarmserver.domain.Obstacle;
import fr.gakkel.swarmsimulator.swarmserver.domain.Predator;
import fr.gakkel.swarmsimulator.swarmserver.domain.PredatorConfig;
import fr.gakkel.swarmsimulator.swarmserver.domain.Vector3D;
import fr.gakkel.swarmsimulator.swarmserver.domain.World;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SimulationLoop {

    private static final Logger LOG = LoggerFactory.getLogger(SimulationLoop.class);
    public static final int  TICK_RATE_HZ          = 30;
    private static final double DT = 1.0 / TICK_RATE_HZ;
    // 1000 / 30 = 33ms period → actual rate ~30.3Hz (integer division); acceptable for diagnostics
    private static final int LOG_INTERVAL_TICKS = 5 * TICK_RATE_HZ;

    private static final double INITIAL_SPEED_XY = 2.0;
    private static final double INITIAL_SPEED_Z  = 1.0;

    private final Simulation simulation;
    private final World world;
    private final BoidsConfig config;
    private final FlockingDiagnostician diagnostician;
    private final CohesionMetric cohesionMetric;
    private final CohesionCsvExporter csvExporter;
    private final ScheduledExecutorService executor;

    private int tickCount = 0;
    private int consecutiveFailedTicks = 0;

    public SimulationLoop(World world, BoidsConfig config, DiagnosticsConfig diagnosticsConfig,
                          ScheduledExecutorService executor) {
        this(world, config, diagnosticsConfig, executor, null);
    }

    public SimulationLoop(World world, BoidsConfig config, DiagnosticsConfig diagnosticsConfig,
                          ScheduledExecutorService executor, CohesionCsvExporter csvExporter) {
        this(new Simulation(world, config, new Random(), new Random()), config, diagnosticsConfig,
                executor, csvExporter);
    }

    /**
     * Canonical constructor. The loop only gives the live pace: every 1/30 s of real time it asks
     * {@code simulation} for one step of 1/30 s of simulation time, then records the metrics
     * shown to the operator. Prefer the {@link #wire} factory, which builds the simulation from
     * one seed.
     */
    SimulationLoop(Simulation simulation, BoidsConfig config, DiagnosticsConfig diagnosticsConfig,
                   ScheduledExecutorService executor, CohesionCsvExporter csvExporter) {
        this.simulation = Objects.requireNonNull(simulation, "simulation");
        this.world = simulation.world();
        this.config = Objects.requireNonNull(config, "config");
        this.diagnostician = new FlockingDiagnostician(config, Objects.requireNonNull(diagnosticsConfig, "diagnosticsConfig"));
        this.cohesionMetric = new CohesionMetric(30);
        this.csvExporter = csvExporter;
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public double cohesionSpreadM() {
        return cohesionMetric.smoothedSpreadM();
    }

    public void start() {
        LOG.info("Simulation started - {}Hz | {} drones | maxSpeed={} | perceptionRadius={}",
                TICK_RATE_HZ, world.agentCount(), config.maxSpeed(), config.perceptionRadius());
        long periodMs = 1000L / TICK_RATE_HZ;
        executor.scheduleAtFixedRate(this::tickSafely, 0, periodMs, TimeUnit.MILLISECONDS);
    }

    public void stop() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
            LOG.warn("sim-loop did not terminate within 1s - forcing shutdown");
            executor.shutdownNow();
        }
        if (csvExporter != null) csvExporter.close();
    }

    // scheduleAtFixedRate cancels a task for good, without any log, the first time it throws.
    // Logging and going on keeps the simulation alive and makes the problem visible.
    // A failure that repeats on each tick would print 30 stack traces per second: the first one
    // has the full stack trace, then one short line per second until a tick succeeds again.
    private void tickSafely() {
        try {
            tick();
            consecutiveFailedTicks = 0;
        } catch (RuntimeException exception) {
            consecutiveFailedTicks++;
            if (consecutiveFailedTicks == 1) {
                LOG.error("Simulation tick {} failed, going on with the next tick", tickCount,
                        exception);
            } else if (consecutiveFailedTicks % TICK_RATE_HZ == 0) {
                LOG.error("Simulation ticks still failing ({} in a row): {}", consecutiveFailedTicks,
                        exception.toString());
            }
        }
    }

    void tick() {
        MDC.put("simulation_tick", String.valueOf(tickCount));
        try {
            simulation.step(DT);

            List<Agent> agents = List.copyOf(world.agents());
            // An empty swarm (all drones eaten) has no centroid: skip it instead of recording NaN.
            if (!agents.isEmpty()) {
                Vector3D centroid = computeCentroid(agents);
                cohesionMetric.record(computePositionStandardDeviation(agents, centroid));
            }

            tickCount++;
            if (csvExporter != null && tickCount % TICK_RATE_HZ == 0) {
                // The wall-clock time only labels the CSV row; the simulation time comes from the clock.
                csvExporter.record(System.currentTimeMillis(), simulation.clock().elapsedSeconds(),
                        cohesionMetric.smoothedSpreadM());
            }
            if (tickCount % LOG_INTERVAL_TICKS == 0) {
                int elapsedSeconds = tickCount / TICK_RATE_HZ;
                double intervalSeconds = (double) LOG_INTERVAL_TICKS / TICK_RATE_HZ;
                diagnostician.record(elapsedSeconds, intervalSeconds, world);
            }
        } finally {
            MDC.remove("simulation_tick");
        }
    }

    static Vector3D computeCentroid(List<Agent> agents) {
        Vector3D sum = Vector3D.ZERO;
        for (Agent a : agents) sum = sum.add(a.position());
        return sum.scale(1.0 / agents.size());
    }

    // POSITIVE_INFINITY when there are no obstacles or no agents — caller treats it as "no constraint"
    static double minObstacleGap(List<Agent> agents, List<Obstacle> obstacles) {
        double min = Double.POSITIVE_INFINITY;
        for (Agent a : agents) {
            for (Obstacle o : obstacles) {
                double gap = a.position().distanceTo(o.position()) - o.radius();
                if (gap < min) min = gap;
            }
        }
        return min;
    }

    static long countAgentsInsideObstacles(List<Agent> agents, List<Obstacle> obstacles) {
        return agents.stream()
                .filter(agent -> obstacles.stream()
                        .anyMatch(obstacle -> agent.position().distanceTo(obstacle.position()) < obstacle.radius()))
                .count();
    }

    static double computePositionStandardDeviation(List<Agent> agents, Vector3D centroid) {
        double sumSq = 0;
        for (Agent a : agents) {
            double dist = a.position().distanceTo(centroid);
            sumSq += dist * dist;
        }
        return Math.sqrt(sumSq / agents.size());
    }

    // Distinct salts so the three RNG streams derived from the single master seed don't share state.
    private static final long RESPAWN_SEED_SALT = 0x9E3779B97F4A7C15L;
    private static final long WANDER_SEED_SALT  = 0xC2B2AE3D27D4EB4FL;

    /** A world, the simulation that moves it and the live loop that drives it, from one master seed. */
    public record Wiring(World world, Simulation simulation, SimulationLoop loop) {}

    /**
     * Builds the world and the simulation loop from a single master seed: initial placement, predator
     * respawn and the wander force all derive from {@code masterSeed}, so the entire run is
     * reproducible. This is the public assembly entry point — callers never touch the low-level
     * {@code createWorld} primitive or the per-stream RNGs directly.
     */
    public static Wiring wire(int agentCount, double width, double height, double depth,
                              BoidsConfig boids, DiagnosticsConfig diagnostics,
                              PredatorConfig predator, long masterSeed,
                              ScheduledExecutorService executor, CohesionCsvExporter csvExporter) {
        World world = createWorld(agentCount, width, height, depth, predator,
                new Random(masterSeed));
        Simulation simulation = new Simulation(world, boids,
                new Random(masterSeed ^ RESPAWN_SEED_SALT), new Random(masterSeed ^ WANDER_SEED_SALT));
        SimulationLoop loop = new SimulationLoop(simulation, boids, diagnostics, executor, csvExporter);
        return new Wiring(world, simulation, loop);
    }

    public static World createDefaultWorld() {
        return createWorld(SimulationConstants.DEFAULT_AGENT_COUNT,
                SimulationConstants.DEFAULT_WORLD_WIDTH, SimulationConstants.DEFAULT_WORLD_HEIGHT,
                SimulationConstants.DEFAULT_WORLD_DEPTH, new Random(SimulationConstants.DEFAULT_SEED));
    }

    static World createWorld(int agentCount, Random rng) {
        return createWorld(agentCount,
                SimulationConstants.DEFAULT_WORLD_WIDTH, SimulationConstants.DEFAULT_WORLD_HEIGHT,
                SimulationConstants.DEFAULT_WORLD_DEPTH, rng);
    }

    // Obstacle/predator positions are expressed as fractions of the world dimensions, and obstacle
    // radii scale by the smallest dimension ratio so a sphere never outgrows the smallest extent.
    // At the default 100x100x50 the scale is 1.0, reproducing the original literal positions
    // (50,-50,25)/(25,-70,25)/(75,-30,25), radii (8/5/6) and predator (5,-5,5) exactly.
    static World createWorld(int agentCount, double width, double height, double depth, Random rng) {
        return createWorld(agentCount, width, height, depth, PredatorConfig.defaults(), rng);
    }

    static World createWorld(int agentCount, double width, double height, double depth,
                             PredatorConfig predatorConfig, Random rng) {
        World world = new World(width, height, depth);
        for (int i = 0; i < agentCount; i++) {
            Vector3D pos = new Vector3D(
                    rng.nextDouble(0, width),
                    rng.nextDouble(-height, 0),
                    rng.nextDouble(0, depth));
            Vector3D vel = new Vector3D(
                    rng.nextDouble(-INITIAL_SPEED_XY, INITIAL_SPEED_XY),
                    rng.nextDouble(-INITIAL_SPEED_XY, INITIAL_SPEED_XY),
                    rng.nextDouble(-INITIAL_SPEED_Z, INITIAL_SPEED_Z));
            world.addAgent(new Agent(UUID.randomUUID(), AgentType.EXPLORER, pos, vel));
        }
        double radiusScale = Math.min(width / SimulationConstants.DEFAULT_WORLD_WIDTH,
                Math.min(height / SimulationConstants.DEFAULT_WORLD_HEIGHT, depth / SimulationConstants.DEFAULT_WORLD_DEPTH));
        world.addObstacle(new Obstacle(new Vector3D(0.50 * width, -0.50 * height, 0.50 * depth), 8.0 * radiusScale));
        world.addObstacle(new Obstacle(new Vector3D(0.25 * width, -0.70 * height, 0.50 * depth), 5.0 * radiusScale));
        world.addObstacle(new Obstacle(new Vector3D(0.75 * width, -0.30 * height, 0.50 * depth), 6.0 * radiusScale));
        Vector3D predatorStart = new Vector3D(0.05 * width, -0.05 * height, 0.10 * depth);
        world.setPredator(
                new Predator(UUID.randomUUID(), predatorStart, Vector3D.ZERO, predatorConfig));
        return world;
    }
}
