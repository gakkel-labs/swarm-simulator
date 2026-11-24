package fr.gakkel.swarmsimulator.swarmserver.simulation;

import fr.gakkel.swarmsimulator.swarmserver.domain.Agent;
import fr.gakkel.swarmsimulator.swarmserver.domain.AgentType;
import fr.gakkel.swarmsimulator.swarmserver.domain.BoidsConfig;
import fr.gakkel.swarmsimulator.swarmserver.domain.BoidsSearchPattern;
import fr.gakkel.swarmsimulator.swarmserver.domain.FleetConfiguration;
import fr.gakkel.swarmsimulator.swarmserver.domain.Obstacle;
import fr.gakkel.swarmsimulator.swarmserver.domain.Predator;
import fr.gakkel.swarmsimulator.swarmserver.domain.PredatorConfig;
import fr.gakkel.swarmsimulator.swarmserver.domain.SearchPattern;
import fr.gakkel.swarmsimulator.swarmserver.domain.SearchPatternSettings;
import fr.gakkel.swarmsimulator.swarmserver.domain.Target;
import fr.gakkel.swarmsimulator.swarmserver.domain.Vector3D;
import fr.gakkel.swarmsimulator.swarmserver.domain.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SimulationTest {

    private static final BoidsConfig CONFIG = BoidsConfig.builder().build();
    private static final double STEP_SECONDS = 1.0 / 30;

    private static Simulation createSimulation(World world) {
        return new Simulation(world, new BoidsSearchPattern(CONFIG, new Random(2L)),
                CONFIG.maxSpeed(), new Random(1L));
    }

    private static Agent createAgent(Vector3D position) {
        return new Agent(UUID.randomUUID(), AgentType.EXPLORER, position, Vector3D.ZERO);
    }

    private static void runSteps(Simulation simulation, int stepCount) {
        for (int step = 0; step < stepCount; step++) simulation.step(STEP_SECONDS);
    }

    @Nested
    class Clock {

        @Test
        void newSimulation_startsAtZeroSeconds() {
            Simulation simulation = createSimulation(new World(100, 100, 50));

            assertEquals(0.0, simulation.clock().elapsedSeconds());
        }

        @Test
        void step_advancesTheSimulationClockByTheStepDuration() {
            Simulation simulation = createSimulation(WorldFactory.createWorld(10, new Random(0L)));

            simulation.step(0.1);
            simulation.step(0.2);

            assertEquals(0.3, simulation.clock().elapsedSeconds(), 1e-12);
        }

        @Test
        void step_zeroDuration_throwsIllegalArgumentException() {
            Simulation simulation = createSimulation(new World(100, 100, 50));

            assertThrows(IllegalArgumentException.class, () -> simulation.step(0.0));
        }

        @Test
        void oneMinuteOfSimulation_runsFasterThanRealTime() {
            Simulation simulation = createSimulation(WorldFactory.createWorld(20, new Random(0L)));
            long startNanos = System.nanoTime();

            runSteps(simulation, 60 * 30);

            double realSeconds = (System.nanoTime() - startNanos) / 1e9;
            assertEquals(60.0, simulation.clock().elapsedSeconds(), 1e-9);
            assertTrue(realSeconds < 10.0, "60 s of simulation took " + realSeconds + " s");
        }
    }

    @Nested
    class Reproducibility {

        private static final int THIRTY_SECONDS_OF_STEPS = 30 * 30;

        /**
         * Golden master: the fingerprint of the drones after 30 s with seed 42, recorded before
         * the search pattern interface (#81). If it changes, Boids no longer moves the drones
         * exactly as before: find why before updating it.
         */
        private static final String SEED_42_FINGERPRINT =
                "f5ac8aa7842997388b58c95427271df7d07f97ed677c5f03ee1ab7374f31a989";

        private static Simulation createSeededSimulation(long seed) {
            FleetConfiguration fleet =
                    new FleetConfiguration(20, new SearchPatternSettings.Boids(CONFIG));
            return Simulation.create(fleet, 100, 100, 50, PredatorConfig.defaults(), seed);
        }

        /** What a run leaves: each drone id with its position, in id order. */
        private static List<String> finalDrones(Simulation simulation) {
            return simulation.world().agents().stream()
                    .sorted((first, second) -> first.id().compareTo(second.id()))
                    .map(agent -> agent.id() + " " + agent.position() + " " + agent.velocity())
                    .toList();
        }

        @Test
        void sameSeedTwice_givesStrictlyEqualDronesAfterThirtySeconds() {
            Simulation firstRun = createSeededSimulation(42L);
            Simulation secondRun = createSeededSimulation(42L);

            runSteps(firstRun, THIRTY_SECONDS_OF_STEPS);
            runSteps(secondRun, THIRTY_SECONDS_OF_STEPS);

            // No tolerance: the same seed must give the very same numbers, bit for bit.
            assertEquals(finalDrones(firstRun), finalDrones(secondRun));
        }

        @Test
        void seed42_givesTheSameDronesAsBeforeTheSearchPatternInterface() {
            Simulation simulation = createSeededSimulation(42L);

            runSteps(simulation, THIRTY_SECONDS_OF_STEPS);

            assertEquals(SEED_42_FINGERPRINT, sha256(String.join("\n", finalDrones(simulation))));
        }

        private static String sha256(String text) {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256")
                        .digest(text.getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(digest);
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("every Java runtime has SHA-256", exception);
            }
        }

        @Test
        void differentSeeds_giveDifferentDrones() {
            Simulation firstRun = createSeededSimulation(42L);
            Simulation secondRun = createSeededSimulation(43L);

            runSteps(firstRun, THIRTY_SECONDS_OF_STEPS);
            runSteps(secondRun, THIRTY_SECONDS_OF_STEPS);

            assertNotEquals(finalDrones(firstRun), finalDrones(secondRun));
        }

        @Test
        void orderInWhichDronesWereAdded_doesNotChangeTheResult() {
            List<Agent> drones = WorldFactory.createWorld(20, new Random(7L)).agents();
            World worldInOrder = new World(100, 100, 50);
            World worldReversed = new World(100, 100, 50);
            for (Agent drone : drones) worldInOrder.addAgent(copyOf(drone));
            for (Agent drone : drones.reversed()) worldReversed.addAgent(copyOf(drone));
            Simulation inOrder = createSimulation(worldInOrder);
            Simulation reversed = createSimulation(worldReversed);

            runSteps(inOrder, 300);
            runSteps(reversed, 300);

            assertEquals(finalDrones(inOrder), finalDrones(reversed));
        }

        private static Agent copyOf(Agent drone) {
            return new Agent(drone.id(), drone.type(), drone.position(), drone.velocity());
        }
    }

    @Nested
    class Movement {

        @Test
        void drones_followTheForceOfTheSearchPattern() {
            World world = new World(100, 100, 50);
            Agent drone = createAgent(new Vector3D(50, -50, 25));
            world.addAgent(drone);
            SearchPattern alwaysEast = (anyDrone, anyWorld) -> new Vector3D(1, 0, 0);
            Simulation simulation =
                    new Simulation(world, alwaysEast, CONFIG.maxSpeed(), new Random(1L));

            simulation.step(1.0);

            assertEquals(new Vector3D(51, -50, 25), drone.position());
        }

        @Test
        void searchPatternForce_isLimitedByTheMaxSpeed() {
            World world = new World(100, 100, 50);
            Agent drone = createAgent(new Vector3D(50, -50, 25));
            world.addAgent(drone);
            SearchPattern strongEast = (anyDrone, anyWorld) -> new Vector3D(100, 0, 0);
            Simulation simulation = new Simulation(world, strongEast, 2.0, new Random(1L));

            simulation.step(1.0);

            assertEquals(2.0, drone.velocity().magnitude(), 1e-9);
        }

        @Test
        void constructor_maxSpeedNotPositive_throwsIllegalArgumentException() {
            World world = new World(100, 100, 50);
            SearchPattern standStill = (anyDrone, anyWorld) -> Vector3D.ZERO;

            for (double maxSpeed : new double[] {0.0, -1.0, Double.NaN}) {
                assertThrows(IllegalArgumentException.class,
                        () -> new Simulation(world, standStill, maxSpeed, new Random(1L)),
                        "maxSpeed " + maxSpeed);
            }
        }

        @Test
        void drones_stayInTheSearchAreaAfterManySteps() {
            World world = WorldFactory.createWorld(10, new Random(0L));
            Simulation simulation = createSimulation(world);

            runSteps(simulation, 300);

            for (Agent agent : world.agents()) {
                assertTrue(world.isInBounds(agent.position()), "drone out of bounds: " + agent.position());
            }
        }

        @Test
        void drones_neverExceedMaxSpeed() {
            World world = WorldFactory.createWorld(10, new Random(0L));
            Simulation simulation = createSimulation(world);

            runSteps(simulation, 300);

            for (Agent agent : world.agents()) {
                assertTrue(agent.velocity().magnitude() <= CONFIG.maxSpeed() + 1e-9,
                        "speed exceeds maxSpeed: " + agent.velocity().magnitude());
            }
        }

        @Test
        void drones_neverInsideObstaclesAfterManySteps() {
            World world = WorldFactory.createWorld(20, new Random(42L));
            Simulation simulation = createSimulation(world);

            runSteps(simulation, 600);

            assertEquals(0, SimulationLoop.countAgentsInsideObstacles(world.agents(), world.obstacles()));
        }
    }

    @Nested
    class Respawn {

        private World world;
        private Simulation simulation;

        @BeforeEach
        void setUp() {
            world = new World(100, 100, 50);
            world.setPredator(new Predator(new Vector3D(10, -50, 25), Vector3D.ZERO));
            world.addAgent(Agent.create(AgentType.EXPLORER, new Vector3D(10.5, -50, 25))); // in eat radius
            simulation = createSimulation(world);
        }

        @Test
        void eatenDrone_isStillAbsentJustBeforeTheDelay() {
            simulation.step(STEP_SECONDS);
            assertEquals(0, world.agentCount(), "the drone is eaten in the first step");

            runSteps(simulation, 148); // 149 steps in all: just under 5 s after the meal

            assertEquals(0, world.agentCount());
        }

        @Test
        void eatenDrone_comesBackAfterFiveSecondsOfSimulation() {
            simulation.step(STEP_SECONDS);

            runSteps(simulation, 155); // a few steps past the 5 s delay

            assertEquals(1, world.agentCount());
        }
    }

    @Nested
    class TargetDetection {

        private static final Vector3D TARGET_POSITION = new Vector3D(50, -50, 25);

        private World world;
        private Simulation simulation;

        @BeforeEach
        void setUp() {
            world = new World(100, 100, 50);
            simulation = createSimulation(world);
        }

        private Target placeTarget() {
            Target target = new Target(TARGET_POSITION, simulation.clock());
            world.setTarget(target);
            return target;
        }

        @Test
        void droneWithinSensorRadius_marksTargetFound() {
            Target target = placeTarget();
            Agent nearDrone = createAgent(new Vector3D(50 + SimulationConstants.SENSOR_RADIUS_M - 1, -50, 25));
            world.addAgent(nearDrone);

            simulation.step(STEP_SECONDS);

            assertTrue(target.isFound());
            assertEquals(nearDrone.id().toString(), target.foundByAgentId());
        }

        @Test
        void droneBeyondSensorRadius_doesNotMarkFound() {
            Target target = placeTarget();
            world.addAgent(createAgent(new Vector3D(50 + SimulationConstants.SENSOR_RADIUS_M + 1, -50, 25)));

            simulation.step(STEP_SECONDS);

            assertFalse(target.isFound());
        }

        @Test
        void droneExactlyAtSensorRadius_marksTargetFound() {
            Target target = placeTarget();
            world.addAgent(createAgent(new Vector3D(50 + SimulationConstants.SENSOR_RADIUS_M, -50, 25)));

            simulation.step(STEP_SECONDS);

            assertTrue(target.isFound());
        }

        @Test
        void alreadyFoundTarget_notDetectedAgain() {
            Target target = placeTarget();
            target.markFound("first-drone");
            world.addAgent(createAgent(TARGET_POSITION));

            simulation.step(STEP_SECONDS);

            assertEquals("first-drone", target.foundByAgentId());
        }

        @Test
        void discoveryTime_isTheSimulationTimeSinceTheTargetWasPlaced() {
            simulation.step(0.5);
            Target target = placeTarget();
            world.addAgent(createAgent(TARGET_POSITION));

            simulation.step(0.25);

            assertEquals(0.25, target.foundAtElapsedS(), 1e-12);
        }

        @Test
        void droneEatenInTheSameStep_doesNotFindTheTarget() {
            Target target = placeTarget();
            world.setPredator(new Predator(TARGET_POSITION, Vector3D.ZERO));
            world.addAgent(createAgent(TARGET_POSITION)); // on the target and in the eat radius

            simulation.step(STEP_SECONDS);

            assertEquals(0, world.agentCount(), "the drone is eaten in this step");
            assertFalse(target.isFound(), "a lost drone cannot report the target");
        }

        @Test
        void noTarget_stepRunsWithoutError() {
            world.addAgent(createAgent(TARGET_POSITION));

            assertDoesNotThrow(() -> simulation.step(STEP_SECONDS));
        }
    }

    @Test
    void clampSpeed_noChangeWhenBelowMax() {
        Vector3D velocity = new Vector3D(1, 0, 0);
        assertEquals(velocity, Simulation.clampSpeed(velocity, 5.0));
    }

    @Test
    void clampSpeed_scalesDownWhenAboveMax() {
        Vector3D clamped = Simulation.clampSpeed(new Vector3D(10, 0, 0), 5.0);
        assertEquals(5.0, clamped.magnitude(), 1e-9);
    }

    @Nested
    class ResolveObstacleCollision {

        @Test
        void outsideObstacle_nothingChanges() {
            var position = new Vector3D(20, 0, 0);
            var velocity = new Vector3D(-5, 0, 0);
            var obstacle = new Obstacle(new Vector3D(0, 0, 0), 5.0);

            var result = Simulation.resolveObstacleCollision(position, velocity, obstacle);

            assertEquals(position, result.position());
            assertEquals(velocity, result.velocity());
        }

        @Test
        void droneInsideObstacle_ejectedToSurface() {
            var position = new Vector3D(2, 0, 0);
            var obstacle = new Obstacle(new Vector3D(0, 0, 0), 5.0);

            var result = Simulation.resolveObstacleCollision(position, Vector3D.ZERO, obstacle);

            assertEquals(5.0, result.position().distanceTo(obstacle.position()), 1e-9);
        }

        @Test
        void droneMovingInward_inwardComponentZeroed() {
            var position = new Vector3D(2, 0, 0);
            var velocity = new Vector3D(-3, 2, 0);
            var obstacle = new Obstacle(new Vector3D(0, 0, 0), 5.0);

            var result = Simulation.resolveObstacleCollision(position, velocity, obstacle);

            // outward direction is +X; tangential (+Y) must be preserved, inward (-X) zeroed
            assertEquals(0.0, result.velocity().x(), 1e-9);
            assertEquals(2.0, result.velocity().y(), 1e-9);
        }

        @Test
        void droneMovingOutward_velocityPreserved() {
            var position = new Vector3D(2, 0, 0);
            var velocity = new Vector3D(3, 0, 0);
            var obstacle = new Obstacle(new Vector3D(0, 0, 0), 5.0);

            var result = Simulation.resolveObstacleCollision(position, velocity, obstacle);

            assertEquals(velocity, result.velocity());
        }

        @Test
        void droneAtObstacleCenter_fallbackEjectionUpward() {
            var position = new Vector3D(0, 0, 0);
            var obstacle = new Obstacle(new Vector3D(0, 0, 0), 5.0);

            var result = Simulation.resolveObstacleCollision(position, Vector3D.ZERO, obstacle);

            assertEquals(5.0, result.position().distanceTo(obstacle.position()), 1e-9);
        }
    }

    @Nested
    class ConstrainVelocityToWalls {

        private static final World SEARCH_AREA = new World(100, 100, 50);

        @Test
        void insideBounds_velocityUnchanged() {
            // Y=-50: valid underwater position (surface=0, floor=-100)
            var velocity = new Vector3D(1, -1, 1);
            assertEquals(velocity,
                    Simulation.constrainVelocityToWalls(new Vector3D(50, -50, 25), velocity, SEARCH_AREA));
        }

        @Test
        void belowFloor_downwardVelocityZeroed() {
            // Y=-101: 1m below sea floor (floor at -100)
            var result = Simulation.constrainVelocityToWalls(
                    new Vector3D(50, -101, 25), new Vector3D(2, -3, 1), SEARCH_AREA);
            assertEquals(0.0, result.y(), 1e-9);
            assertEquals(2.0, result.x(), 1e-9);
            assertEquals(1.0, result.z(), 1e-9);
        }

        @Test
        void belowFloor_upwardVelocityPreserved() {
            // Y=-101: below floor but moving up → allowed
            var velocity = new Vector3D(0, 3, 0);
            assertEquals(velocity,
                    Simulation.constrainVelocityToWalls(new Vector3D(50, -101, 25), velocity, SEARCH_AREA));
        }

        @Test
        void aboveSurface_upwardVelocityZeroed() {
            // Y=1: 1m above water surface (surface at Y=0)
            var result = Simulation.constrainVelocityToWalls(
                    new Vector3D(50, 1, 25), new Vector3D(1, 5, 0), SEARCH_AREA);
            assertEquals(0.0, result.y(), 1e-9);
        }

        @Test
        void beyondXWall_outwardVelocityZeroed() {
            var result = Simulation.constrainVelocityToWalls(
                    new Vector3D(101, 50, 25), new Vector3D(4, 1, 0), SEARCH_AREA);
            assertEquals(0.0, result.x(), 1e-9);
        }
    }
}
