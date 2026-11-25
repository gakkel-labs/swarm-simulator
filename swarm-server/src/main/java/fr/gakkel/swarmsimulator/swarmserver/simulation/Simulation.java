package fr.gakkel.swarmsimulator.swarmserver.simulation;

import fr.gakkel.swarmsimulator.swarmserver.domain.Agent;
import fr.gakkel.swarmsimulator.swarmserver.domain.BoidsSearchPattern;
import fr.gakkel.swarmsimulator.swarmserver.domain.EndConditions;
import fr.gakkel.swarmsimulator.swarmserver.domain.FleetConfiguration;
import fr.gakkel.swarmsimulator.swarmserver.domain.LossRiskLevel;
import fr.gakkel.swarmsimulator.swarmserver.domain.Mission;
import fr.gakkel.swarmsimulator.swarmserver.domain.Obstacle;
import fr.gakkel.swarmsimulator.swarmserver.domain.Predator;
import fr.gakkel.swarmsimulator.swarmserver.domain.Scenario;
import fr.gakkel.swarmsimulator.swarmserver.domain.SearchPattern;
import fr.gakkel.swarmsimulator.swarmserver.domain.SearchPatternSettings;
import fr.gakkel.swarmsimulator.swarmserver.domain.SimulationClock;
import fr.gakkel.swarmsimulator.swarmserver.domain.SimulationEvent;
import fr.gakkel.swarmsimulator.swarmserver.domain.SimulationEvent.DroneLost;
import fr.gakkel.swarmsimulator.swarmserver.domain.SimulationEvent.DroneRespawned;
import fr.gakkel.swarmsimulator.swarmserver.domain.SimulationEvent.EndReason;
import fr.gakkel.swarmsimulator.swarmserver.domain.SimulationEvent.LossCause;
import fr.gakkel.swarmsimulator.swarmserver.domain.SimulationEvent.RunEnded;
import fr.gakkel.swarmsimulator.swarmserver.domain.SimulationEvent.TargetDetected;
import fr.gakkel.swarmsimulator.swarmserver.domain.Target;
import fr.gakkel.swarmsimulator.swarmserver.domain.Vector3D;
import fr.gakkel.swarmsimulator.swarmserver.domain.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * The simulation engine: {@link #step} moves the world forward by a given duration of
 * simulation time and returns what happened (the {@link SimulationEvent}s of the step). It has no
 * thread, no timer, no log and no gRPC, so it can run live (driven by {@link SimulationLoop} at
 * 30 Hz) or as fast as the computer allows, without display. The demo side effects (predator,
 * respawn) come only from {@link DemoOptions}.
 *
 * <p>Only one thread runs a simulation: it calls {@link #step} and reads {@link #isOver},
 * {@link #runEnd} and {@link #lostDroneIds}. Other threads only read the {@link World} and the
 * {@link SimulationClock}, which are made for it (ADR-0008).
 */
public final class Simulation {

    static final double RESPAWN_DELAY_SECONDS = 5.0;
    private static final double RESPAWN_MIN_DISTANCE_M = 30.0;
    private static final double INITIAL_SPEED_XY = 2.0;
    private static final double INITIAL_SPEED_Z = 1.0;
    // Steps of 1/30 s never add up to a round number of seconds as doubles, and the error grows
    // with each step. Without this margin, the run would end one step after its limit time.
    // Relative to the limit: 1e-9 covers millions of steps (each sum loses at most half a unit
    // of the last digit, about 1e-16 of the value).
    private static final double LIMIT_TIME_RELATIVE_TOLERANCE = 1e-9;

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
    private final boolean respawnEatenDrones;
    private final EndConditions endConditions;
    private final SimulationClock clock = new SimulationClock();
    private final List<PendingRespawn> pendingRespawns = new ArrayList<>();
    private final List<UUID> lostDroneIds = new ArrayList<>();
    private RunEnded runEnd;

    /**
     * A live demo simulation without loss risk: it never ends, and the drones eaten by a predator
     * placed in {@code world} come back. {@code searchPattern} says where each drone wants to go;
     * the simulation then applies the same physics to every pattern: the cruise speed of each
     * drone (from its specification), obstacles and walls. {@code respawnRandom} places the
     * drones that come back after the demo predator ate them.
     */
    public Simulation(World world, SearchPattern searchPattern, Random respawnRandom) {
        this(world, searchPattern, respawnRandom, LossRiskLevel.NONE, new Random(0L), true,
                EndConditions.never());
    }

    /**
     * A simulation where each drone may be lost while it dives, at the rate of {@code lossRisk}.
     * {@code lossRandom} draws which drones are lost. {@code respawnEatenDrones} brings the drones
     * eaten by the demo predator back (live demo only), and {@code endConditions} says when the
     * run stops.
     */
    public Simulation(World world, SearchPattern searchPattern, Random respawnRandom,
                      LossRiskLevel lossRisk, Random lossRandom, boolean respawnEatenDrones,
                      EndConditions endConditions) {
        this.world = Objects.requireNonNull(world, "world");
        this.searchPattern = Objects.requireNonNull(searchPattern, "searchPattern");
        this.respawnRandom = Objects.requireNonNull(respawnRandom, "respawnRandom");
        this.lossRisk = Objects.requireNonNull(lossRisk, "lossRisk");
        this.lossRandom = Objects.requireNonNull(lossRandom, "lossRandom");
        this.respawnEatenDrones = respawnEatenDrones;
        this.endConditions = Objects.requireNonNull(endConditions, "endConditions");
    }

    /**
     * Builds a world and its simulation from a fleet configuration and a scenario. Everything
     * random in the run comes from the seed of the scenario: drone ids and start positions,
     * respawn places, the random part of the search pattern and the lost drones. The generators
     * belong to this simulation only: never share them with another simulation or thread.
     *
     * <p>A scoring simulation passes {@link DemoOptions#none()} and
     * {@link EndConditions#missionEnd}; the
     * live demo passes its demo options and {@link EndConditions#never()}.
     *
     * @param mission  the search area, the flat seabed and the loss risk
     * @param scenario the wreck, placed at time 0 (or no wreck), and the master seed
     * @throws IllegalArgumentException if the wreck is outside the search area, or if a mission
     *                                  run (end conditions {@code missionEnd}) has demo options
     */
    public static Simulation create(FleetConfiguration fleet, Mission mission, Scenario scenario,
                                    EndConditions endConditions, DemoOptions demo) {
        if (endConditions instanceof EndConditions.MissionEnd && !demo.equals(DemoOptions.none())) {
            throw new IllegalArgumentException(
                    "a mission run has no demo option (predator, respawn), got " + demo);
        }
        long masterSeed = scenario.seed();
        World world = WorldFactory.createWorld(fleet.droneGroups(), mission.searchArea(),
                mission.seabedDepthMeters(), demo, new Random(masterSeed));
        LossRiskLevel lossRisk = mission.lossRisk();
        Random searchPatternRandom = new Random(masterSeed ^ SEARCH_PATTERN_SEED_SALT);
        // A factory: the configuration only names the pattern and its settings, and each run
        // gets a new pattern object with its own random generator.
        Simulation simulation = switch (fleet.searchPattern()) {
            case SearchPatternSettings.Boids boids -> new Simulation(world,
                    new BoidsSearchPattern(boids.config(), searchPatternRandom),
                    new Random(masterSeed ^ RESPAWN_SEED_SALT), lossRisk,
                    new Random(masterSeed ^ LOSS_RISK_SEED_SALT), demo.respawnEatenDrones(),
                    endConditions);
        };
        // No random draw here: the same seed gives the same swarm with or without a wreck.
        scenario.wreckPosition().ifPresent(wreckPosition -> {
            if (!world.isInBounds(wreckPosition)) {
                throw new IllegalArgumentException(
                        "wreck " + wreckPosition + " is outside the search area");
            }
            world.setTarget(new Target(wreckPosition, simulation.clock()));
        });
        return simulation;
    }

    public World world() {
        return world;
    }

    public SimulationClock clock() {
        return clock;
    }

    /**
     * The drones lost for good, in the order they were lost: to the loss risk, or to the demo
     * predator when respawn is off. They never come back.
     */
    public List<UUID> lostDroneIds() {
        return List.copyOf(lostDroneIds);
    }

    /**
     * Runs the simulation to its end, as fast as possible, with steps of {@code stepSeconds}, and
     * returns what happened. Only for a run that ends (a mission run): the live demo never ends.
     *
     * @throws IllegalStateException if the run never ends or has already ended
     */
    public SimulationResult runToEnd(double stepSeconds) {
        if (endConditions instanceof EndConditions.Never) {
            throw new IllegalStateException("this simulation never ends: it cannot run to its end");
        }
        if (isOver()) {
            throw new IllegalStateException("the run ended at " + runEnd.atSeconds() + " s");
        }
        List<SimulationEvent> events = new ArrayList<>();
        while (!isOver()) {
            events.addAll(step(stepSeconds));
        }
        return new SimulationResult(runEnd, events);
    }

    /** True once the run has ended: no more step is allowed. */
    public boolean isOver() {
        return runEnd != null;
    }

    /** How and when the run ended, or empty while it goes on. */
    public Optional<RunEnded> runEnd() {
        return Optional.ofNullable(runEnd);
    }

    /**
     * Moves the world forward by {@code stepSeconds} of simulation time and returns what happened,
     * in order. The order inside a step: drones move, the clock advances, some drones may be lost
     * (loss risk), the demo predator hunts, the target is checked, the drones whose respawn delay
     * is over come back, then the end conditions are checked.
     *
     * @throws IllegalStateException if the run is already over
     */
    public List<SimulationEvent> step(double stepSeconds) {
        // Checked first so a bad step changes nothing in the world.
        if (!(stepSeconds > 0)) {
            throw new IllegalArgumentException("step must be positive, got " + stepSeconds);
        }
        if (isOver()) {
            throw new IllegalStateException("the run ended at " + runEnd.atSeconds() + " s");
        }
        List<SimulationEvent> events = new ArrayList<>();
        List<Agent> agents = List.copyOf(world.agents());
        moveDrones(agents, stepSeconds);
        clock.advance(stepSeconds);
        loseDronesAtRisk(stepSeconds, events);
        huntWithPredator(stepSeconds, events);
        // Only the drones still in the world: a drone lost in this step cannot report the target.
        checkTargetDetection(world.agents(), events);
        respawnDronesWhoseDelayIsOver(events);
        checkEndConditions(events);
        return List.copyOf(events);
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
    private void loseDronesAtRisk(double stepSeconds, List<SimulationEvent> events) {
        if (lossRisk == LossRiskLevel.NONE) {
            return;
        }
        double lossProbability = lossRisk.lossProbabilityDuring(stepSeconds);
        for (Agent agent : List.copyOf(world.agents())) {
            if (lossRandom.nextDouble() < lossProbability) {
                world.removeAgent(agent);
                lostDroneIds.add(agent.id());
                events.add(new DroneLost(agent.id(), LossCause.LOSS_RISK, clock.elapsedSeconds()));
            }
        }
    }

    private void huntWithPredator(double stepSeconds, List<SimulationEvent> events) {
        Predator predator = world.predator();
        if (predator == null) return;
        predator.update(world, stepSeconds);
        Agent eaten = predator.tryEat(world);
        if (eaten == null) return;
        events.add(new DroneLost(eaten.id(), LossCause.DEMO_PREDATOR, clock.elapsedSeconds()));
        if (respawnEatenDrones) {
            double dueAtSeconds = clock.elapsedSeconds() + RESPAWN_DELAY_SECONDS;
            pendingRespawns.add(new PendingRespawn(eaten, dueAtSeconds));
        } else {
            lostDroneIds.add(eaten.id());
        }
    }

    private void checkTargetDetection(List<Agent> agents, List<SimulationEvent> events) {
        Target target = world.target();
        if (target == null || target.isFound()) return;
        for (Agent agent : agents) {
            // A sphere of the sensor range for now: the side-scan swath comes with its own issue.
            double detectionRange = agent.specification().searchSensor().maxRangeMeters();
            if (agent.position().distanceTo(target.position()) <= detectionRange) {
                target.markFound(agent.id().toString());
                events.add(new TargetDetected(agent.id(), clock.elapsedSeconds()));
                break;
            }
        }
    }

    // Drones come back in the order they were eaten, so the random draws keep the same order.
    private void respawnDronesWhoseDelayIsOver(List<SimulationEvent> events) {
        Iterator<PendingRespawn> iterator = pendingRespawns.iterator();
        while (iterator.hasNext()) {
            PendingRespawn pending = iterator.next();
            if (clock.elapsedSeconds() < pending.dueAtSeconds()) continue;
            iterator.remove();
            respawn(pending.agent());
            events.add(new DroneRespawned(pending.agent().id(), clock.elapsedSeconds()));
        }
    }

    private void checkEndConditions(List<SimulationEvent> events) {
        Optional<EndReason> reason = switch (endConditions) {
            case EndConditions.Never never -> Optional.empty();
            case EndConditions.MissionEnd missionEnd -> missionEndReason(missionEnd, events);
        };
        reason.ifPresent(endReason -> {
            runEnd = new RunEnded(endReason, clock.elapsedSeconds());
            events.add(runEnd);
        });
    }

    // A wreck found within the limit time wins over the other reasons of the same step: the
    // mission succeeded. A step that goes past the limit time is a failure, even if the wreck is
    // seen in it: the engine cannot tell when, inside the step, the drone saw it.
    private Optional<EndReason> missionEndReason(EndConditions.MissionEnd missionEnd,
                                                 List<SimulationEvent> events) {
        double limitTimeSeconds = missionEnd.limitTimeSeconds();
        double toleranceSeconds = limitTimeSeconds * LIMIT_TIME_RELATIVE_TOLERANCE;
        double elapsedSeconds = clock.elapsedSeconds();
        boolean targetDetected = events.stream()
                .anyMatch(event -> event instanceof TargetDetected);
        if (targetDetected && elapsedSeconds <= limitTimeSeconds + toleranceSeconds) {
            return Optional.of(EndReason.TARGET_DETECTED);
        }
        if (elapsedSeconds >= limitTimeSeconds - toleranceSeconds) {
            return Optional.of(EndReason.LIMIT_TIME_REACHED);
        }
        if (world.agents().isEmpty() && pendingRespawns.isEmpty()) {
            return Optional.of(EndReason.NO_DRONE_LEFT);
        }
        return Optional.empty();
    }

    private void respawn(Agent agent) {
        Vector3D position = findRespawnPosition(world.predator());
        Vector3D velocity = new Vector3D(
                respawnRandom.nextDouble(-INITIAL_SPEED_XY, INITIAL_SPEED_XY),
                respawnRandom.nextDouble(-INITIAL_SPEED_XY, INITIAL_SPEED_XY),
                respawnRandom.nextDouble(-INITIAL_SPEED_Z, INITIAL_SPEED_Z));
        agent.update(position, velocity);
        world.addAgent(agent);
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
