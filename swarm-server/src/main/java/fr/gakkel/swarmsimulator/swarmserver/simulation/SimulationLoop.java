package fr.gakkel.swarmsimulator.swarmserver.simulation;

import fr.gakkel.swarmsimulator.swarmserver.domain.Agent;
import fr.gakkel.swarmsimulator.swarmserver.domain.BoidsConfig;
import fr.gakkel.swarmsimulator.swarmserver.domain.FleetConfiguration;
import fr.gakkel.swarmsimulator.swarmserver.domain.Obstacle;
import fr.gakkel.swarmsimulator.swarmserver.domain.PredatorConfig;
import fr.gakkel.swarmsimulator.swarmserver.domain.SearchPatternSettings;
import fr.gakkel.swarmsimulator.swarmserver.domain.Vector3D;
import fr.gakkel.swarmsimulator.swarmserver.domain.World;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SimulationLoop {

    private static final Logger LOG = LoggerFactory.getLogger(SimulationLoop.class);
    public static final int  TICK_RATE_HZ          = 30;
    private static final double DT = 1.0 / TICK_RATE_HZ;
    // 1000 / 30 = 33ms period → actual rate ~30.3Hz (integer division); acceptable for diagnostics
    private static final int LOG_INTERVAL_TICKS = 5 * TICK_RATE_HZ;

    private final Simulation simulation;
    private final World world;
    private final BoidsConfig config;
    private final FlockingDiagnostician diagnostician;
    private final CohesionMetric cohesionMetric;
    private final CohesionCsvExporter csvExporter;
    private final ScheduledExecutorService executor;

    private int tickCount = 0;
    private int consecutiveFailedTicks = 0;

    /**
     * The loop only gives the live pace: every 1/30 s of real time it asks
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

    /** A world, the simulation that moves it and the live loop that drives it, from one master seed. */
    public record Wiring(World world, Simulation simulation, SimulationLoop loop) {}

    /**
     * Builds the world and the simulation loop from a fleet configuration and a single master
     * seed: {@code fleet} gives the number of drones and the search pattern; initial placement,
     * respawn and the random part of the search pattern all derive from {@code masterSeed}, so
     * the entire run is reproducible. This is the public assembly entry point — callers never touch
     * {@link WorldFactory} or the per-stream RNGs directly.
     */
    public static Wiring wire(FleetConfiguration fleet, double width, double height, double depth,
                              DiagnosticsConfig diagnostics, PredatorConfig predator,
                              long masterSeed, ScheduledExecutorService executor,
                              CohesionCsvExporter csvExporter) {
        Simulation simulation = Simulation.create(fleet, width, height, depth, predator,
                masterSeed);
        // The live diagnostics watch a flock, so they need the Boids settings. #156 moves the
        // speed limit out of Boids, then the diagnostics will no longer need this switch.
        BoidsConfig boidsConfig = switch (fleet.searchPattern()) {
            case SearchPatternSettings.Boids boids -> boids.config();
        };
        SimulationLoop loop = new SimulationLoop(simulation, boidsConfig, diagnostics, executor,
                csvExporter);
        return new Wiring(simulation.world(), simulation, loop);
    }
}
