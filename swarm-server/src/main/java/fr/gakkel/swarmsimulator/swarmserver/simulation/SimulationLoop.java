package fr.gakkel.swarmsimulator.swarmserver.simulation;

import fr.gakkel.swarmsimulator.swarmserver.domain.Agent;
import fr.gakkel.swarmsimulator.swarmserver.domain.AgentType;
import fr.gakkel.swarmsimulator.swarmserver.domain.BoidsConfig;
import fr.gakkel.swarmsimulator.swarmserver.domain.BoidsRules;
import fr.gakkel.swarmsimulator.swarmserver.domain.Vector3D;
import fr.gakkel.swarmsimulator.swarmserver.domain.World;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    static final int TICK_RATE_HZ = 30;
    private static final double DT = 1.0 / TICK_RATE_HZ;
    private static final int LOG_INTERVAL_TICKS = 5 * TICK_RATE_HZ;

    private final World world;
    private final BoidsRules rules;
    private final BoidsConfig config;
    private final ScheduledExecutorService executor;

    private int tickCount = 0;
    private Vector3D lastCentroid = null;

    public SimulationLoop(World world, BoidsConfig config) {
        this.world = Objects.requireNonNull(world, "world");
        this.config = Objects.requireNonNull(config, "config");
        this.rules = new BoidsRules(config);
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sim-loop");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
    LOG.info(
        "Simulation demarree — {}Hz | {} agents | maxSpeed={} | perceptionRadius={} Appuyez sur [Entree] pour arreter.",
        TICK_RATE_HZ,
        world.agentCount(),
        config.maxSpeed(),
        config.perceptionRadius());
        long periodMs = 1000L / TICK_RATE_HZ;
        executor.scheduleAtFixedRate(this::tick, 0, periodMs, TimeUnit.MILLISECONDS);
    }

    public void stop() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);
    }

    // snapshot steer forces before applying — parallel Boids update, all forces
    // computed from time t so order of iteration doesn't affect the result
    void tick() {
        List<Agent> agents = world.agents();
        List<Vector3D> steers = new ArrayList<>(agents.size());
        for (Agent agent : agents) {
            List<Agent> neighbors = world.neighbors(agent, config.perceptionRadius());
            Vector3D steer = rules.steer(agent, neighbors)
                    .add(rules.boundaryRepulsion(agent, world).scale(config.boundaryRepulsionWeight()));
            steers.add(steer);
        }
        for (int i = 0; i < agents.size(); i++) {
            Agent agent = agents.get(i);
            Vector3D newVelocity = clampSpeed(agent.velocity().add(steers.get(i).scale(DT)), config.maxSpeed());
            Vector3D newPosition = world.clamp(agent.position().add(newVelocity.scale(DT)));
            agent.update(newPosition, newVelocity);
        }
        tickCount++;
        if (tickCount % LOG_INTERVAL_TICKS == 0) {
            logCentroid();
        }
    }

    private void logCentroid() {
        List<Agent> agents = world.agents();
        if (agents.isEmpty()) return;

        Vector3D sum = Vector3D.ZERO;
        for (Agent a : agents) {
            sum = sum.add(a.position());
        }
        Vector3D centroid = sum.scale(1.0 / agents.size());

        double spreadSum = 0;
        for (Agent a : agents) {
            spreadSum += a.position().distanceTo(centroid);
        }
        double spread = spreadSum / agents.size();

        double intervalSeconds = (double) LOG_INTERVAL_TICKS / TICK_RATE_HZ;
        String driftInfo;
        if (lastCentroid == null) {
            driftInfo = "premier releve";
        } else {
            double drift = centroid.distanceTo(lastCentroid) / intervalSeconds;
            String status = drift < 0.1 ? "essaim bloque (verifier separation)" : "mouvement coherent";
            driftInfo = String.format("drift=%.2f u/s — %s", drift, status);
        }
        lastCentroid = centroid;

        LOG.info("t={}s | {} | spread={} u", tickCount / TICK_RATE_HZ, driftInfo, Math.round(spread * 10) / 10.0);
    }

    static Vector3D clampSpeed(Vector3D velocity, double maxSpeed) {
        double mag = velocity.magnitude();
        if (mag > maxSpeed) return velocity.scale(maxSpeed / mag);
        return velocity;
    }

    public static World createDefaultWorld() {
        return createWorld(20, new Random(42L));
    }

    static World createWorld(int agentCount, Random rng) {
        World world = new World(100, 100, 50);
        for (int i = 0; i < agentCount; i++) {
            Vector3D pos = new Vector3D(
                    rng.nextDouble(0, 100),
                    rng.nextDouble(0, 100),
                    rng.nextDouble(0, 50));
            Vector3D vel = new Vector3D(
                    rng.nextDouble(-2, 2),
                    rng.nextDouble(-2, 2),
                    rng.nextDouble(-1, 1));
            world.addAgent(new Agent(UUID.randomUUID(), AgentType.EXPLORER, pos, vel));
        }
        return world;
    }

    public static void main(String[] args) throws Exception {
        BoidsConfig config = BoidsConfig.builder().build();
        World world = createDefaultWorld();
        SimulationLoop loop = new SimulationLoop(world, config);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { loop.stop(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }));

        loop.start();
        //noinspection ResultOfMethodCallIgnored
        System.in.read();
        loop.stop();
    }
}
