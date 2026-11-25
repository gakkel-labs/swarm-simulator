package fr.gakkel.swarmsimulator.swarmserver.simulation;

import fr.gakkel.swarmsimulator.swarmserver.domain.Agent;
import fr.gakkel.swarmsimulator.swarmserver.domain.BoidsSearchPattern;
import fr.gakkel.swarmsimulator.swarmserver.domain.FleetConfiguration;
import fr.gakkel.swarmsimulator.swarmserver.domain.LossRiskLevel;
import fr.gakkel.swarmsimulator.swarmserver.domain.Obstacle;
import fr.gakkel.swarmsimulator.swarmserver.domain.Predator;
import fr.gakkel.swarmsimulator.swarmserver.domain.PredatorConfig;
import fr.gakkel.swarmsimulator.swarmserver.domain.SearchPattern;
import fr.gakkel.swarmsimulator.swarmserver.domain.SearchPatternSettings;
import fr.gakkel.swarmsimulator.swarmserver.domain.SimulationClock;
import fr.gakkel.swarmsimulator.swarmserver.domain.Target;
import fr.gakkel.swarmsimulator.swarmserver.domain.Vector3D;
import fr.gakkel.swarmsimulator.swarmserver.domain.World;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * The simulation engine: {@link #step} moves the world forward by a given duration of
 * simulation time. It has no thread, no timer and no gRPC, so it can run live (driven by
 * {@link SimulationLoop} at 30 Hz) or as fast as the computer allows, without display.
 */
public final class Simulation {

    private static final Logger LOG = LoggerFactory.getLogger(Simulation.class);

    static final double RESPAWN_DELAY_SECONDS = 5.0;
    private static final double RESPAWN_MIN_DISTANCE_M = 30.0;
    private static final double INITIAL_SPEED_XY = 2.0;
    private static final double INITIAL_SPEED_Z = 1.0;

    // Distinct salts so the random streams derived from the one master seed don't share state.
    private static final long RESPAWN_SEED_SALT = 0x9E3779B97F4A7C15L;
    private static final long SEARCH_PATTERN_SEED_SALT = 0xC2B2AE3D27D4EB4FL;
    private static final long LOSS_RISK_SEED_SALT = 0x165667B19E3779F9L;

    /** A drone eaten by the demo predator, waiting to come back into the search area. */
    private record PendingRespawn(Agent agent, double dueAtSeconds) {}

    private final World world;
    private final SearchPattern searchPattern;
    private final Random respawnRandom;
    private final LossRiskLevel lossRisk;
    private final Random lossRandom;
    private final SimulationClock clock = new SimulationClock();
    private final List<PendingRespawn> pendingRespawns = new ArrayList<>();
    private final List<UUID> lostDroneIds = new ArrayList<>();

    /**
     * A simulation without loss risk. {@code searchPattern} says where each drone wants to go;
     * the simulation then applies the same physics to every pattern: the cruise speed of each
     * drone (from its specification), obstacles and walls. {@code respawnRandom} places the
     * drones that come back after the demo predator ate them.
     */
    public Simulation(World world, SearchPattern searchPattern, Random respawnRandom) {
        this(world, searchPattern, respawnRandom, LossRiskLevel.NONE, new Random(0L));
    }

    /**
     * A simulation where each drone may be lost while it dives, at the rate of {@code lossRisk}.
     * {@code lossRandom} draws which drones are lost.
     */
    public Simulation(World world, SearchPattern searchPattern, Random respawnRandom,
                      LossRiskLevel lossRisk, Random lossRandom) {
        this.world = Objects.requireNonNull(world, "world");
        this.searchPattern = Objects.requireNonNull(searchPattern, "searchPattern");
        this.respawnRandom = Objects.requireNonNull(respawnRandom, "respawnRandom");
        this.lossRisk = Objects.requireNonNull(lossRisk, "lossRisk");
        this.lossRandom = Objects.requireNonNull(lossRandom, "lossRandom");
    }

    /**
     * A simulation without the demo predator, as batch runs will use: see
     * {@link #create(FleetConfiguration, double, double, double, LossRiskLevel, Optional, long)}.
     */
    public static Simulation create(FleetConfiguration fleet, double width, double height,
                                    double depth, LossRiskLevel lossRisk, long masterSeed) {
        return create(fleet, width, height, depth, lossRisk, Optional.empty(), masterSeed);
    }

    /**
     * Builds a world and its simulation from a fleet configuration and one master seed.
     * Everything random in the run comes from that seed: drone ids and start positions, respawn
     * places, the random part of the search pattern and the lost drones. The generators belong
     * to this simulation only: never share them with another simulation or thread.
     *
     * @param demoPredator the predator of the live demo, or empty for none. Batch runs never
     *                     have one: the predator is not a mission concept (ADR-0012).
     */
    public static Simulation create(FleetConfiguration fleet, double width, double height,
                                    double depth, LossRiskLevel lossRisk,
                                    Optional<PredatorConfig> demoPredator, long masterSeed) {
        World world = WorldFactory.createWorld(fleet.droneGroups(), width, height, depth,
                demoPredator, new Random(masterSeed));
        Random searchPatternRandom = new Random(masterSeed ^ SEARCH_PATTERN_SEED_SALT);
        // A factory: the configuration only names the pattern and its settings, and each run
        // gets a new pattern object with its own random generator.
        return switch (fleet.searchPattern()) {
            case SearchPatternSettings.Boids boids -> new Simulation(world,
                    new BoidsSearchPattern(boids.config(), searchPatternRandom),
                    new Random(masterSeed ^ RESPAWN_SEED_SALT), lossRisk,
                    new Random(masterSeed ^ LOSS_RISK_SEED_SALT));
        };
    }

    public World world() {
        return world;
    }

    public SimulationClock clock() {
        return clock;
    }

    /** The drones lost to the loss risk, in the order they were lost. They never come back. */
    public List<UUID> lostDroneIds() {
        return List.copyOf(lostDroneIds);
    }

    /**
     * Moves the world forward by {@code stepSeconds} of simulation time. The order inside a step:
     * drones move, the clock advances, some drones may be lost (loss risk), the demo predator
     * hunts, the target is checked, then the drones whose respawn delay is over come back.
     */
    public void step(double stepSeconds) {
        // Checked first so a bad step changes nothing in the world.
        if (!(stepSeconds > 0)) {
            throw new IllegalArgumentException("step must be positive, got " + stepSeconds);
        }
        List<Agent> agents = List.copyOf(world.agents());
        moveDrones(agents, stepSeconds);
        clock.advance(stepSeconds);
        loseDronesAtRisk(stepSeconds);
        huntWithPredator(stepSeconds);
        // Only the drones still in the world: a drone lost in this step cannot report the target.
        checkTargetDetection(world.agents());
        respawnDronesWhoseDelayIsOver();
    }

    // Steer forces are all computed from the positions at time t before any drone moves,
    // so the order of the drones does not change the forces. A bad force is refused before
    // any drone moves: NaN would pass the speed limit and reach the display.
    private void moveDrones(List<Agent> agents, double stepSeconds) {
        List<Vector3D> steers = new ArrayList<>(agents.size());
        for (Agent agent : agents) {
            Vector3D steer = searchPattern.steer(agent, world);
            if (!isFinite(steer)) {
                throw new IllegalStateException("search pattern gave the force " + steer
                        + " to drone " + agent.id());
            }
            steers.add(steer);
        }
        for (int index = 0; index < agents.size(); index++) {
            Agent agent = agents.get(index);
            Vector3D newVelocity = clampSpeed(
                    agent.velocity().add(steers.get(index).scale(stepSeconds)),
                    agent.specification().cruiseSpeedMetersPerSecond());
            Vector3D newPosition = agent.position().add(newVelocity.scale(stepSeconds));
            for (Obstacle obstacle : world.obstacles()) {
                ObstacleCollision collision = resolveObstacleCollision(newPosition, newVelocity, obstacle);
                newPosition = collision.position();
                newVelocity = collision.velocity();
            }
            newVelocity = constrainVelocityToWalls(newPosition, newVelocity, world);
            newPosition = world.clamp(newPosition);
            agent.update(newPosition, newVelocity);
        }
    }

    // One draw per drone, in the sorted order of the world, so the same seed loses the same
    // drones. With no risk there is no draw at all: the run is the same as before #130.
    private void loseDronesAtRisk(double stepSeconds) {
        if (lossRisk == LossRiskLevel.NONE) return;
        double lossProbability = lossRisk.lossProbabilityDuring(stepSeconds);
        for (Agent agent : List.copyOf(world.agents())) {
            if (lossRandom.nextDouble() < lossProbability) {
                world.removeAgent(agent);
                lostDroneIds.add(agent.id());
                LOG.info("drone {} lost (loss risk {})", agent.id(), lossRisk);
            }
        }
    }

    private void huntWithPredator(double stepSeconds) {
        Predator predator = world.predator();
        if (predator == null) return;
        predator.update(world, stepSeconds);
        Agent eaten = predator.tryEat(world);
        if (eaten != null) {
            double dueAtSeconds = clock.elapsedSeconds() + RESPAWN_DELAY_SECONDS;
            pendingRespawns.add(new PendingRespawn(eaten, dueAtSeconds));
            LOG.info("drone {} eaten - respawn in {}s of simulation", eaten.id(), RESPAWN_DELAY_SECONDS);
        }
    }

    private void checkTargetDetection(List<Agent> agents) {
        Target target = world.target();
        if (target == null || target.isFound()) return;
        for (Agent agent : agents) {
            // A sphere of the sensor range for now: the side-scan swath comes with its own issue.
            double detectionRange = agent.specification().searchSensor().maxRangeMeters();
            if (agent.position().distanceTo(target.position()) <= detectionRange) {
                target.markFound(agent.id().toString());
                LOG.atInfo()
                   .setMessage("Target found by drone {} in {}s")
                   .addArgument(agent.id())
                   .addArgument(() -> String.format(Locale.ROOT, "%.1f", target.foundAtElapsedS()))
                   .log();
                break;
            }
        }
    }

    // Drones come back in the order they were eaten, so the random draws keep the same order.
    private void respawnDronesWhoseDelayIsOver() {
        Iterator<PendingRespawn> iterator = pendingRespawns.iterator();
        while (iterator.hasNext()) {
            PendingRespawn pending = iterator.next();
            if (clock.elapsedSeconds() < pending.dueAtSeconds()) continue;
            iterator.remove();
            respawn(pending.agent());
        }
    }

    private void respawn(Agent agent) {
        Vector3D position = findRespawnPosition(world.predator());
        Vector3D velocity = new Vector3D(
                respawnRandom.nextDouble(-INITIAL_SPEED_XY, INITIAL_SPEED_XY),
                respawnRandom.nextDouble(-INITIAL_SPEED_XY, INITIAL_SPEED_XY),
                respawnRandom.nextDouble(-INITIAL_SPEED_Z, INITIAL_SPEED_Z));
        agent.update(position, velocity);
        world.addAgent(agent);
        LOG.info("drone {} respawned", agent.id());
    }

    private Vector3D findRespawnPosition(Predator predator) {
        for (int attempt = 0; attempt < 20; attempt++) {
            Vector3D position = new Vector3D(
                    respawnRandom.nextDouble(0, world.width()),
                    respawnRandom.nextDouble(-world.height(), 0),
                    respawnRandom.nextDouble(0, world.depth()));
            if (predator == null || position.distanceTo(predator.position()) >= RESPAWN_MIN_DISTANCE_M) {
                return position;
            }
        }
        return new Vector3D(world.width() / 2, -world.height() / 2, world.depth() / 2);
    }

    private static boolean isFinite(Vector3D vector) {
        return vector != null && Double.isFinite(vector.x()) && Double.isFinite(vector.y())
                && Double.isFinite(vector.z());
    }

    static Vector3D clampSpeed(Vector3D velocity, double maxSpeed) {
        double magnitude = velocity.magnitude();
        if (magnitude > maxSpeed) return velocity.scale(maxSpeed / magnitude);
        return velocity;
    }

    record ObstacleCollision(Vector3D position, Vector3D velocity) {}

    static ObstacleCollision resolveObstacleCollision(Vector3D position, Vector3D velocity, Obstacle obstacle) {
        double distance = position.distanceTo(obstacle.position());
        if (distance >= obstacle.radius()) return new ObstacleCollision(position, velocity);
        Vector3D outward = distance > 1e-10
                ? position.subtract(obstacle.position()).scale(1.0 / distance)
                : new Vector3D(0, 1, 0);
        Vector3D ejected = obstacle.position().add(outward.scale(obstacle.radius()));
        double radial = velocity.dot(outward);
        Vector3D corrected = radial < 0 ? velocity.subtract(outward.scale(radial)) : velocity;
        return new ObstacleCollision(ejected, corrected);
    }

    static Vector3D constrainVelocityToWalls(Vector3D position, Vector3D velocity, World world) {
        double vx = velocity.x();
        double vy = velocity.y();
        double vz = velocity.z();
        if (position.x() < 0               && vx < 0) vx = 0;
        if (position.x() > world.width()   && vx > 0) vx = 0;
        if (position.y() < -world.height() && vy < 0) vy = 0;
        if (position.y() > 0               && vy > 0) vy = 0;
        if (position.z() < 0               && vz < 0) vz = 0;
        if (position.z() > world.depth()   && vz > 0) vz = 0;
        if (vx == velocity.x() && vy == velocity.y() && vz == velocity.z()) return velocity;
        return new Vector3D(vx, vy, vz);
    }
}
