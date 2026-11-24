package fr.gakkel.swarmsimulator.swarmserver.simulation;

import fr.gakkel.swarmsimulator.swarmserver.domain.Agent;
import fr.gakkel.swarmsimulator.swarmserver.domain.BoidsConfig;
import fr.gakkel.swarmsimulator.swarmserver.domain.BoidsRules;
import fr.gakkel.swarmsimulator.swarmserver.domain.Obstacle;
import fr.gakkel.swarmsimulator.swarmserver.domain.Predator;
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
import java.util.Random;

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

    /** A drone eaten by the predator, waiting to come back into the search area. */
    private record PendingRespawn(Agent agent, double dueAtSeconds) {}

    private final World world;
    private final BoidsConfig config;
    private final BoidsRules rules;
    private final Random respawnRandom;
    private final SimulationClock clock = new SimulationClock();
    private final List<PendingRespawn> pendingRespawns = new ArrayList<>();

    /**
     * {@code respawnRandom} places the drones that come back after the predator ate them;
     * {@code wanderRandom} drives the Boids wander force.
     */
    public Simulation(World world, BoidsConfig config, Random respawnRandom, Random wanderRandom) {
        this.world = Objects.requireNonNull(world, "world");
        this.config = Objects.requireNonNull(config, "config");
        this.rules = new BoidsRules(config, Objects.requireNonNull(wanderRandom, "wanderRandom"));
        this.respawnRandom = Objects.requireNonNull(respawnRandom, "respawnRandom");
    }

    public World world() {
        return world;
    }

    public SimulationClock clock() {
        return clock;
    }

    /**
     * Moves the world forward by {@code stepSeconds} of simulation time. The order inside a step:
     * drones move, the clock advances, the predator hunts, the target is checked, then the
     * drones whose respawn delay is over come back.
     */
    public void step(double stepSeconds) {
        // Checked first so a bad step changes nothing in the world.
        if (!(stepSeconds > 0)) {
            throw new IllegalArgumentException("step must be positive, got " + stepSeconds);
        }
        List<Agent> agents = List.copyOf(world.agents());
        moveDrones(agents, stepSeconds);
        clock.advance(stepSeconds);
        huntWithPredator(stepSeconds);
        checkTargetDetection(agents);
        respawnDronesWhoseDelayIsOver();
    }

    // Steer forces are all computed from the positions at time t before any drone moves,
    // so the order of the drones does not change the forces.
    private void moveDrones(List<Agent> agents, double stepSeconds) {
        List<Vector3D> steers = new ArrayList<>(agents.size());
        for (Agent agent : agents) {
            List<Agent> neighbors = world.neighbors(agent, config.perceptionRadius());
            steers.add(rules.steer(agent, neighbors, world));
        }
        for (int index = 0; index < agents.size(); index++) {
            Agent agent = agents.get(index);
            Vector3D newVelocity = clampSpeed(
                    agent.velocity().add(steers.get(index).scale(stepSeconds)), config.maxSpeed());
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
            if (agent.position().distanceTo(target.position()) <= SimulationConstants.SENSOR_RADIUS_M) {
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
