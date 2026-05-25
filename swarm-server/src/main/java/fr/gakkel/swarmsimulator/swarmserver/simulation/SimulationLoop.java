package fr.gakkel.swarmsimulator.swarmserver.simulation;

import fr.gakkel.swarmsimulator.swarmserver.domain.Agent;
import fr.gakkel.swarmsimulator.swarmserver.domain.AgentType;
import fr.gakkel.swarmsimulator.swarmserver.domain.BoidsConfig;
import fr.gakkel.swarmsimulator.swarmserver.domain.BoidsRules;
import fr.gakkel.swarmsimulator.swarmserver.domain.Obstacle;
import fr.gakkel.swarmsimulator.swarmserver.domain.Predator;
import fr.gakkel.swarmsimulator.swarmserver.domain.Target;
import fr.gakkel.swarmsimulator.swarmserver.domain.Vector3D;
import fr.gakkel.swarmsimulator.swarmserver.domain.World;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SimulationLoop {

    private static final Logger LOG = LoggerFactory.getLogger(SimulationLoop.class);
    public static final int  TICK_RATE_HZ          = 30;
    private static final long BOID_RESPAWN_DELAY_MS = 5_000L;
    private static final double RESPAWN_MIN_DIST  = 30.0;
    private static final double DT = 1.0 / TICK_RATE_HZ;
    // 1000 / 30 = 33ms period → actual rate ~30.3Hz (integer division); acceptable for diagnostics
    private static final int LOG_INTERVAL_TICKS = 5 * TICK_RATE_HZ;

    static final double DEFAULT_WORLD_WIDTH  = 100;
    static final double DEFAULT_WORLD_HEIGHT = 100;
    static final double DEFAULT_WORLD_DEPTH  = 50;
    private static final double INITIAL_SPEED_XY = 2.0;
    private static final double INITIAL_SPEED_Z  = 1.0;

    private final World world;
    private final BoidsRules rules;
    private final BoidsConfig config;
    private final DiagnosticsConfig diagnosticsConfig;
    private final ScheduledExecutorService executor;
    private final Random rng = new Random();

    // All fields below are written exclusively on the sim-loop thread (via tick()).
    // Add synchronisation before exposing any of them to an external observer (e.g. gRPC handler).
    private int tickCount = 0;
    private Vector3D lastCentroid = null;
    private double previousPositionStandardDeviation = 0;
    private int consecutiveStandardDeviationIncreases = 0;
    private final List<Double> recentSigmas = new ArrayList<>();
    private volatile boolean flockingConfirmed = false; // volatile: may be polled by future observers

    public SimulationLoop(World world, BoidsConfig config, DiagnosticsConfig diagnosticsConfig,
                          ScheduledExecutorService executor) {
        this.world = Objects.requireNonNull(world, "world");
        this.config = Objects.requireNonNull(config, "config");
        this.diagnosticsConfig = Objects.requireNonNull(diagnosticsConfig, "diagnosticsConfig");
        this.rules = new BoidsRules(config);
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public void start() {
        LOG.info("Simulation demarree — {}Hz | {} agents | maxSpeed={} | perceptionRadius={}",
                TICK_RATE_HZ, world.agentCount(), config.maxSpeed(), config.perceptionRadius());
        long periodMs = 1000L / TICK_RATE_HZ;
        executor.scheduleAtFixedRate(this::tick, 0, periodMs, TimeUnit.MILLISECONDS);
    }

    public void stop() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
            LOG.warn("sim-loop did not terminate within 1s — forcing shutdown");
            executor.shutdownNow();
        }
    }

    // snapshot steer forces before applying — parallel Boids update, all forces
    // computed from time t so order of iteration doesn't affect the result
    void tick() {
        MDC.put("simulation_tick", String.valueOf(tickCount));
        try {
            List<Agent> agents = List.copyOf(world.agents());
            List<Vector3D> steers = new ArrayList<>(agents.size());
            for (Agent agent : agents) {
                List<Agent> neighbors = world.neighbors(agent, config.perceptionRadius());
                Vector3D steer = rules.steer(agent, neighbors, world);
                steers.add(steer);
            }
            for (int i = 0; i < agents.size(); i++) {
                Agent agent = agents.get(i);
                Vector3D newVelocity = clampSpeed(agent.velocity().add(steers.get(i).scale(DT)), config.maxSpeed());
                Vector3D newPosition = agent.position().add(newVelocity.scale(DT));
                for (Obstacle obstacle : world.obstacles()) {
                    ObstacleCollision c = resolveObstacleCollision(newPosition, newVelocity, obstacle);
                    newPosition = c.position();
                    newVelocity = c.velocity();
                }
                newVelocity = constrainVelocityToWalls(newPosition, newVelocity, world);
                newPosition = world.clamp(newPosition);
                agent.update(newPosition, newVelocity);
            }
            Predator predator = world.predator();
            if (predator != null) {
                predator.update(world, DT);
                Agent eaten = predator.tryEat(world);
                if (eaten != null) {
                    LOG.info("agent {} eaten — respawn in {}ms", eaten.id(), BOID_RESPAWN_DELAY_MS);
                    scheduleRespawn(eaten, predator);
                }
            }

            checkTargetDetection(agents);

            tickCount++;
            if (tickCount % LOG_INTERVAL_TICKS == 0) {
                logCentroid();
            }
        } finally {
            MDC.remove("simulation_tick");
        }
    }

    private void checkTargetDetection(List<Agent> agents) {
        Target target = world.target();
        if (target == null || target.isFound()) return;
        for (Agent agent : agents) {
            if (agent.position().distanceTo(target.position()) <= SimulationConstants.SENSOR_RADIUS_M) {
                target.markFound(agent.id().toString());
                LOG.atInfo()
                   .setMessage("Target found by agent {} in {}s")
                   .addArgument(agent.id())
                   .addArgument(() -> String.format("%.1f", target.foundAtElapsedS()))
                   .log();
                break;
            }
        }
    }

    private void scheduleRespawn(Agent agent, Predator predator) {
        executor.schedule(() -> {
            Vector3D pos = findRespawnPosition(predator);
            Vector3D vel = new Vector3D(
                    rng.nextDouble(-INITIAL_SPEED_XY, INITIAL_SPEED_XY),
                    rng.nextDouble(-INITIAL_SPEED_XY, INITIAL_SPEED_XY),
                    rng.nextDouble(-INITIAL_SPEED_Z, INITIAL_SPEED_Z));
            agent.update(pos, vel);
            world.addAgent(agent);
            LOG.info("agent {} respawned", agent.id());
        }, BOID_RESPAWN_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private Vector3D findRespawnPosition(Predator predator) {
        for (int i = 0; i < 20; i++) {
            Vector3D pos = new Vector3D(
                    rng.nextDouble(0, DEFAULT_WORLD_WIDTH),
                    rng.nextDouble(-DEFAULT_WORLD_HEIGHT, 0),
                    rng.nextDouble(0, DEFAULT_WORLD_DEPTH));
            if (pos.distanceTo(predator.position()) >= RESPAWN_MIN_DIST) return pos;
        }
        return new Vector3D(DEFAULT_WORLD_WIDTH / 2, -DEFAULT_WORLD_HEIGHT / 2, DEFAULT_WORLD_DEPTH / 2);
    }

    private void logCentroid() {
        List<Agent> agents = world.agents();
        if (agents.isEmpty()) return;

        Vector3D centroid = computeCentroid(agents);
        double positionStandardDeviation = computePositionStandardDeviation(agents, centroid);
        int elapsed = tickCount / TICK_RATE_HZ;
        double standardDeviationRounded = Math.round(positionStandardDeviation * 10) / 10.0;

        if (lastCentroid == null) {
            LOG.info("t={}s | premier releve | σ={} u", elapsed, standardDeviationRounded);
        } else {
            double intervalSeconds = (double) LOG_INTERVAL_TICKS / TICK_RATE_HZ;
            double drift = centroid.distanceTo(lastCentroid) / intervalSeconds;
            double driftRounded = Math.round(drift * 100) / 100.0;
            LOG.info("t={}s | drift={} u/s | σ={} u", elapsed, driftRounded, standardDeviationRounded);
            checkAlerts(elapsed, drift, driftRounded, positionStandardDeviation, standardDeviationRounded);
            checkFlocking(elapsed, positionStandardDeviation, standardDeviationRounded);
        }

        if (!world.obstacles().isEmpty()) {
            logObstacleAvoidance(elapsed);
        }

        lastCentroid = centroid;
        previousPositionStandardDeviation = positionStandardDeviation;
    }

    private void logObstacleAvoidance(int elapsed) {
        List<Agent> agents = world.agents();
        long penetrations = countAgentsInsideObstacles(agents, world.obstacles());
        double gap = minObstacleGap(agents, world.obstacles());
        double gapRounded = Math.round(gap * 10) / 10.0;
        if (penetrations > 0) {
            LOG.warn("t={}s | ALERTE {} agent(s) dans obstacle (gap min={} u)", elapsed, penetrations, gapRounded);
        } else {
            LOG.info("t={}s | min obstacle gap={} u", elapsed, gapRounded);
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
                .filter(a -> obstacles.stream()
                        .anyMatch(o -> a.position().distanceTo(o.position()) < o.radius()))
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

    private void checkAlerts(int elapsed, double drift, double driftRounded, double positionStandardDeviation, double standardDeviationRounded) {
        if (drift < config.maxSpeed() * diagnosticsConfig.immobileThresholdFraction()) {
            LOG.warn("t={}s | ALERTE essaim immobile (drift={} u/s)", elapsed, driftRounded);
        }

        if (!flockingConfirmed) {
            double standardDeviationLimit = Math.min(world.width(), Math.min(world.height(), world.depth()))
                    * diagnosticsConfig.dispersalLimitFraction();
            if (positionStandardDeviation > standardDeviationLimit) {
                LOG.warn("t={}s | ALERTE essaim disperse (σ={} u > {} u)", elapsed, standardDeviationRounded, (int) standardDeviationLimit);
            }
        }

        if (positionStandardDeviation > previousPositionStandardDeviation) {
            consecutiveStandardDeviationIncreases++;
            if (consecutiveStandardDeviationIncreases >= diagnosticsConfig.stabilitySamples()) {
                LOG.warn("t={}s | ALERTE divergence ({} relevés en hausse)", elapsed, consecutiveStandardDeviationIncreases);
            }
        } else {
            consecutiveStandardDeviationIncreases = 0;
        }

        long frozenCount = world.agents().stream()
                .filter(a -> a.velocity().magnitude() < config.maxSpeed() * diagnosticsConfig.frozenThresholdFraction())
                .count();
        if (frozenCount > 0) {
            LOG.warn("t={}s | ALERTE {} agent(s) figé(s)", elapsed, frozenCount);
        }
    }

    private void checkFlocking(int elapsed, double positionStandardDeviation, double standardDeviationRounded) {
        if (flockingConfirmed) {
            double criticalLimit = Math.min(world.width(), Math.min(world.height(), world.depth()))
                    * diagnosticsConfig.flockingLostFraction();
            if (positionStandardDeviation > criticalLimit) {
                flockingConfirmed = false;
                recentSigmas.clear();
                LOG.warn("t={}s | flocking lost — σ={} u depasse seuil critique ({} u)",
                        elapsed, standardDeviationRounded, (int) criticalLimit);
            }
            return;
        }
        recentSigmas.add(positionStandardDeviation);
        int samples = diagnosticsConfig.stabilitySamples();
        if (recentSigmas.size() > samples) recentSigmas.removeFirst();
        if (recentSigmas.size() == samples) {
            double max = recentSigmas.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            double min = recentSigmas.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            if (max > 0 && (max - min) / max < diagnosticsConfig.stabilityTolerance()) {
                flockingConfirmed = true;
                LOG.info("t={}s | flocking confirmed — σ stable à {} u", elapsed, standardDeviationRounded);
            }
        }
    }

    static Vector3D clampSpeed(Vector3D velocity, double maxSpeed) {
        double mag = velocity.magnitude();
        if (mag > maxSpeed) return velocity.scale(maxSpeed / mag);
        return velocity;
    }

    record ObstacleCollision(Vector3D position, Vector3D velocity) {}

    static ObstacleCollision resolveObstacleCollision(Vector3D position, Vector3D velocity, Obstacle obstacle) {
        double dist = position.distanceTo(obstacle.position());
        if (dist >= obstacle.radius()) return new ObstacleCollision(position, velocity);
        Vector3D outward = dist > 1e-10
                ? position.subtract(obstacle.position()).scale(1.0 / dist)
                : new Vector3D(0, 1, 0);
        Vector3D ejected = obstacle.position().add(outward.scale(obstacle.radius()));
        double radial = velocity.dot(outward);
        Vector3D corrected = radial < 0 ? velocity.subtract(outward.scale(radial)) : velocity;
        return new ObstacleCollision(ejected, corrected);
    }

    static Vector3D constrainVelocityToWalls(Vector3D position, Vector3D velocity, World world) {
        double vx = velocity.x();
        double vy = velocity.y();
        double
                vz = velocity.z();
        if (position.x() < 0              && vx < 0) vx = 0;
        if (position.x() > world.width()  && vx > 0) vx = 0;
        if (position.y() < -world.height() && vy < 0) vy = 0;
        if (position.y() > 0              && vy > 0) vy = 0;
        if (position.z() < 0              && vz < 0) vz = 0;
        if (position.z() > world.depth()  && vz > 0) vz = 0;
        if (vx == velocity.x() && vy == velocity.y() && vz == velocity.z()) return velocity;
        return new Vector3D(vx, vy, vz);
    }

    public static World createDefaultWorld() {
        return createWorld(20, new Random(42L));
    }

    static World createWorld(int agentCount, Random rng) {
        World world = new World(DEFAULT_WORLD_WIDTH, DEFAULT_WORLD_HEIGHT, DEFAULT_WORLD_DEPTH);
        for (int i = 0; i < agentCount; i++) {
            Vector3D pos = new Vector3D(
                    rng.nextDouble(0, DEFAULT_WORLD_WIDTH),
                    rng.nextDouble(-DEFAULT_WORLD_HEIGHT, 0),
                    rng.nextDouble(0, DEFAULT_WORLD_DEPTH));
            Vector3D vel = new Vector3D(
                    rng.nextDouble(-INITIAL_SPEED_XY, INITIAL_SPEED_XY),
                    rng.nextDouble(-INITIAL_SPEED_XY, INITIAL_SPEED_XY),
                    rng.nextDouble(-INITIAL_SPEED_Z, INITIAL_SPEED_Z));
            world.addAgent(new Agent(UUID.randomUUID(), AgentType.EXPLORER, pos, vel));
        }
        world.addObstacle(new Obstacle(new Vector3D(50, -50, 25), 8.0));
        world.addObstacle(new Obstacle(new Vector3D(25, -70, 25), 5.0));
        world.addObstacle(new Obstacle(new Vector3D(75, -30, 25), 6.0));
        world.setPredator(new Predator(new Vector3D(5, -5, 5), Vector3D.ZERO));
        return world;
    }

    public static void main(String[] args) throws Exception {
        BoidsConfig config = BoidsConfig.builder().build();
        DiagnosticsConfig diagnostics = DiagnosticsConfig.builder().build();
        World world = createDefaultWorld();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sim-loop");
            t.setDaemon(true);
            return t;
        });
        SimulationLoop loop = new SimulationLoop(world, config, diagnostics, executor);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { loop.stop(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }));

        loop.start();
        //noinspection ResultOfMethodCallIgnored
        System.in.read();
        loop.stop();
    }
}
