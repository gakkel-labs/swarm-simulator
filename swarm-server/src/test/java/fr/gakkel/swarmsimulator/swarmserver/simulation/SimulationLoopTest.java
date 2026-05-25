package fr.gakkel.swarmsimulator.swarmserver.simulation;


import fr.gakkel.swarmsimulator.swarmserver.domain.Agent;
import fr.gakkel.swarmsimulator.swarmserver.domain.AgentType;
import fr.gakkel.swarmsimulator.swarmserver.domain.BoidsConfig;
import fr.gakkel.swarmsimulator.swarmserver.domain.Obstacle;
import fr.gakkel.swarmsimulator.swarmserver.domain.Predator;
import fr.gakkel.swarmsimulator.swarmserver.domain.Vector3D;
import fr.gakkel.swarmsimulator.swarmserver.domain.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulationLoopTest {

    private static final BoidsConfig CONFIG = BoidsConfig.builder().build();
    private static final DiagnosticsConfig DIAGNOSTICS = DiagnosticsConfig.builder().build();

    private World world;
    private SimulationLoop loop;

    @BeforeEach
    void setUp() {
        world = SimulationLoop.createWorld(10, new Random(0L));
        loop = new SimulationLoop(world, CONFIG, DIAGNOSTICS, mock(ScheduledExecutorService.class));
    }

    @Test
    void createDefaultWorld_creates20Agents() {
        assertEquals(20, SimulationLoop.createDefaultWorld().agentCount());
    }

    @Test
    void createDefaultWorld_agentsStartInBounds() {
        World defaultWorld = SimulationLoop.createDefaultWorld();
        for (Agent agent : defaultWorld.agents()) {
            assertTrue(defaultWorld.isInBounds(agent.position()), "agent out of bounds: " + agent.position());
        }
    }

    @Test
    void tick_agentsRemainInBoundsAfterManyTicks() {
        for (int i = 0; i < 300; i++) loop.tick();
        for (Agent a : world.agents()) {
            assertTrue(world.isInBounds(a.position()), "agent out of bounds after 300 ticks: " + a.position());
        }
    }

    @Test
    void tick_velocityNeverExceedsMaxSpeed() {
        for (int i = 0; i < 300; i++) loop.tick();
        for (Agent a : world.agents()) {
            assertTrue(a.velocity().magnitude() <= CONFIG.maxSpeed() + 1e-9,
                    "velocity exceeds maxSpeed: " + a.velocity().magnitude());
        }
    }

    @Test
    void clampSpeed_noChangeWhenBelowMax() {
        Vector3D vector = new Vector3D(1, 0, 0);
        assertEquals(vector, SimulationLoop.clampSpeed(vector, 5.0));
    }

    @Test
    void clampSpeed_scalesDownWhenAboveMax() {
        Vector3D clamped = SimulationLoop.clampSpeed(new Vector3D(10, 0, 0), 5.0);
        assertEquals(5.0, clamped.magnitude(), 1e-9);
    }

    @Nested
    class ComputeCentroid {

        @Test
        void singleAgent_centroidEqualsItsPosition() {
            var agent = createAgent(new Vector3D(10, 20, 30));
            assertEquals(new Vector3D(10, 20, 30), SimulationLoop.computeCentroid(List.of(agent)));
        }

        @Test
        void twoSymmetricAgents_centroidIsOrigin() {
            var leftAgent  = createAgent(new Vector3D(-5, 0, 0));
            var rightAgent = createAgent(new Vector3D( 5, 0, 0));
            assertEquals(new Vector3D(0, 0, 0), SimulationLoop.computeCentroid(List.of(leftAgent, rightAgent)));
        }
    }

    @Nested
    class ComputePositionStandardDeviation {

        @Test
        void allAgentsAtSamePosition_sigmaIsZero() {
            var firstAgent  = createAgent(new Vector3D(5, 5, 5));
            var secondAgent = createAgent(new Vector3D(5, 5, 5));
            Vector3D centroid = SimulationLoop.computeCentroid(List.of(firstAgent, secondAgent));
            assertEquals(0.0, SimulationLoop.computePositionStandardDeviation(List.of(firstAgent, secondAgent), centroid), 1e-9);
        }

        @Test
        void twoAgentsSymmetric_sigmaEqualsDistanceToCentroid() {
            // each agent is 5u from centroid → σ = sqrt((5² + 5²) / 2) = 5
            var firstAgent  = createAgent(new Vector3D(0, 0, 0));
            var secondAgent = createAgent(new Vector3D(10, 0, 0));
            Vector3D centroid = SimulationLoop.computeCentroid(List.of(firstAgent, secondAgent));
            assertEquals(5.0, SimulationLoop.computePositionStandardDeviation(List.of(firstAgent, secondAgent), centroid), 1e-9);
        }

        @Test
        void scatteredAgents_haveHigherSigmaThanConvergedAgents() {
            var converged = List.of(createAgent(new Vector3D(0, 0, 0)), createAgent(new Vector3D(1, 0, 0)), createAgent(new Vector3D(0, 1, 0)));
            var scattered = List.of(createAgent(new Vector3D(0, 0, 0)), createAgent(new Vector3D(50, 0, 0)), createAgent(new Vector3D(0, 50, 0)));

            double sigmaC = SimulationLoop.computePositionStandardDeviation(converged, SimulationLoop.computeCentroid(converged));
            double sigmaS = SimulationLoop.computePositionStandardDeviation(scattered, SimulationLoop.computeCentroid(scattered));

            assertTrue(sigmaS > sigmaC,
                    "scattered σ=" + sigmaS + " should exceed converged σ=" + sigmaC);
        }
    }

    @Nested
    class MinObstacleGap {

        @Test
        void noObstacles_returnsPositiveInfinity() {
            var agent = createAgent(new Vector3D(50, 50, 25));

            double gap = SimulationLoop.minObstacleGap(List.of(agent), List.of());

            assertEquals(Double.POSITIVE_INFINITY, gap);
        }

        @Test
        void oneAgentOneObstacle_returnsDistanceToSurface() {
            var agent = createAgent(new Vector3D(0, 0, 0));
            var obstacle = new Obstacle(new Vector3D(10, 0, 0), 3.0);

            double gap = SimulationLoop.minObstacleGap(List.of(agent), List.of(obstacle));

            assertEquals(7.0, gap, 1e-9);  // 10 - 3 = 7
        }

        @Test
        void picksSmallestGapAcrossAllPairs() {
            var nearAgent = createAgent(new Vector3D(0, 0, 0));
            var farAgent  = createAgent(new Vector3D(100, 0, 0));
            var farObstacle  = new Obstacle(new Vector3D(50, 0, 0), 1.0);   // gap from nearAgent = 49
            var nearObstacle = new Obstacle(new Vector3D(5, 0, 0), 2.0);    // gap from nearAgent = 3 ← min

            double gap = SimulationLoop.minObstacleGap(List.of(nearAgent, farAgent), List.of(farObstacle, nearObstacle));

            assertEquals(3.0, gap, 1e-9);
        }

        @Test
        void agentInsideObstacle_returnsNegativeGap() {
            var agent = createAgent(new Vector3D(10, 0, 0));
            var obstacle = new Obstacle(new Vector3D(10, 0, 0), 5.0);  // agent at center

            double gap = SimulationLoop.minObstacleGap(List.of(agent), List.of(obstacle));

            assertEquals(-5.0, gap, 1e-9);
        }
    }

    @Nested
    class CountAgentsInsideObstacles {

        @Test
        void noPenetration_returnsZero() {
            var outside = createAgent(new Vector3D(0, 0, 0));
            var obstacle = new Obstacle(new Vector3D(50, 50, 25), 5.0);

            assertEquals(0, SimulationLoop.countAgentsInsideObstacles(List.of(outside), List.of(obstacle)));
        }

        @Test
        void oneInside_returnsOne() {
            var inside  = createAgent(new Vector3D(11, 0, 0));  // 1u from center, radius 3 → inside
            var outside = createAgent(new Vector3D(50, 0, 0));
            var obstacle = new Obstacle(new Vector3D(10, 0, 0), 3.0);

            long count = SimulationLoop.countAgentsInsideObstacles(List.of(inside, outside), List.of(obstacle));

            assertEquals(1, count);
        }

        @Test
        void multipleObstacles_agentCountedOnceEvenIfInsideSeveral() {
            var agent = createAgent(new Vector3D(10, 0, 0));
            var firstObstacle  = new Obstacle(new Vector3D(10, 0, 0), 3.0);  // contains agent
            var secondObstacle = new Obstacle(new Vector3D(11, 0, 0), 3.0);  // also contains agent

            long count = SimulationLoop.countAgentsInsideObstacles(List.of(agent), List.of(firstObstacle, secondObstacle));

            assertEquals(1, count);
        }
    }

    @Test
    void tick_agentsNeverInsideObstaclesAfterManyTicks() {
        World testWorld = SimulationLoop.createWorld(20, new Random(42L));
        SimulationLoop testLoop = new SimulationLoop(testWorld, CONFIG, DIAGNOSTICS, mock(ScheduledExecutorService.class));
        for (int i = 0; i < 600; i++) testLoop.tick();
        long penetrations = SimulationLoop.countAgentsInsideObstacles(testWorld.agents(), testWorld.obstacles());
        assertEquals(0, penetrations, "agents should not be inside obstacles after 600 ticks");
    }

    @Nested
    class ResolveObstacleCollision {

        @Test
        void outsideObstacle_nothingChanges() {
            var pos = new Vector3D(20, 0, 0);
            var vel = new Vector3D(-5, 0, 0);
            var obstacle = new Obstacle(new Vector3D(0, 0, 0), 5.0);

            var result = SimulationLoop.resolveObstacleCollision(pos, vel, obstacle);

            assertEquals(pos, result.position());
            assertEquals(vel, result.velocity());
        }

        @Test
        void agentInsideObstacle_ejectedToSurface() {
            var pos = new Vector3D(2, 0, 0);
            var obstacle = new Obstacle(new Vector3D(0, 0, 0), 5.0);

            var result = SimulationLoop.resolveObstacleCollision(pos, Vector3D.ZERO, obstacle);

            assertEquals(5.0, result.position().distanceTo(obstacle.position()), 1e-9);
        }

        @Test
        void agentMovingInward_inwardComponentZeroed() {
            var pos = new Vector3D(2, 0, 0);
            var vel = new Vector3D(-3, 2, 0);
            var obstacle = new Obstacle(new Vector3D(0, 0, 0), 5.0);

            var result = SimulationLoop.resolveObstacleCollision(pos, vel, obstacle);

            // outward direction is +X; tangential (+Y) must be preserved, inward (-X) zeroed
            assertEquals(0.0, result.velocity().x(), 1e-9);
            assertEquals(2.0, result.velocity().y(), 1e-9);
        }

        @Test
        void agentMovingOutward_velocityPreserved() {
            var pos = new Vector3D(2, 0, 0);
            var vel = new Vector3D(3, 0, 0);
            var obstacle = new Obstacle(new Vector3D(0, 0, 0), 5.0);

            var result = SimulationLoop.resolveObstacleCollision(pos, vel, obstacle);

            assertEquals(vel, result.velocity());
        }

        @Test
        void agentAtObstacleCenter_fallbackEjectionUpward() {
            var pos = new Vector3D(0, 0, 0);
            var obstacle = new Obstacle(new Vector3D(0, 0, 0), 5.0);

            var result = SimulationLoop.resolveObstacleCollision(pos, Vector3D.ZERO, obstacle);

            assertEquals(5.0, result.position().distanceTo(obstacle.position()), 1e-9);
        }
    }

    @Nested
    class ConstrainVelocityToWalls {

        private static final World W = new World(100, 100, 50);

        @Test
        void insideBounds_velocityUnchanged() {
            // Y=-50: valid underwater position (surface=0, floor=-100)
            var vel = new Vector3D(1, -1, 1);
            assertEquals(vel, SimulationLoop.constrainVelocityToWalls(new Vector3D(50, -50, 25), vel, W));
        }

        @Test
        void belowFloor_downwardVelocityZeroed() {
            // Y=-101: 1m below sea floor (floor at -100)
            var result = SimulationLoop.constrainVelocityToWalls(
                    new Vector3D(50, -101, 25), new Vector3D(2, -3, 1), W);
            assertEquals(0.0, result.y(), 1e-9);
            assertEquals(2.0, result.x(), 1e-9);
            assertEquals(1.0, result.z(), 1e-9);
        }

        @Test
        void belowFloor_upwardVelocityPreserved() {
            // Y=-101: below floor but moving up → allowed
            var vel = new Vector3D(0, 3, 0);
            assertEquals(vel, SimulationLoop.constrainVelocityToWalls(new Vector3D(50, -101, 25), vel, W));
        }

        @Test
        void aboveSurface_upwardVelocityZeroed() {
            // Y=1: 1m above water surface (surface at Y=0)
            var result = SimulationLoop.constrainVelocityToWalls(
                    new Vector3D(50, 1, 25), new Vector3D(1, 5, 0), W);
            assertEquals(0.0, result.y(), 1e-9);
        }

        @Test
        void beyondXWall_outwardVelocityZeroed() {
            var result = SimulationLoop.constrainVelocityToWalls(
                    new Vector3D(101, 50, 25), new Vector3D(4, 1, 0), W);
            assertEquals(0.0, result.x(), 1e-9);
        }
    }

    @Test
    void createDefaultWorld_hasPredator() {
        assertNotNull(SimulationLoop.createDefaultWorld().predator());
    }

    @Nested
    class ScheduleRespawn {

        @Test
        void agentReappearsAfterDelayExpires() {
            var testWorld = new World(100, 100, 50);
            var predator  = new Predator(new Vector3D(10, -50, 25), Vector3D.ZERO);
            var agent     = Agent.create(AgentType.EXPLORER,
                    new Vector3D(10.5, -50, 25)); // within EAT_RADIUS (1.5)
            testWorld.addAgent(agent);
            testWorld.setPredator(predator);

            ScheduledExecutorService mockExecutor = mock(ScheduledExecutorService.class);
            var testLoop = new SimulationLoop(testWorld, CONFIG, DIAGNOSTICS, mockExecutor);

            testLoop.tick();

            // agent eaten — world is empty
            assertEquals(0, testWorld.agentCount());

            // capture and run the respawn task
            ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            verify(mockExecutor).schedule(captor.capture(),
                    eq(5_000L), eq(TimeUnit.MILLISECONDS));
            captor.getValue().run();

            assertEquals(1, testWorld.agentCount());
        }
    }

    @Nested
    class CheckTargetDetection {

        private World detectionWorld;
        private SimulationLoop detectionLoop;

        @BeforeEach
        void setUp() {
            detectionWorld = new World(100, 100, 50);
            detectionLoop = new SimulationLoop(detectionWorld, CONFIG, DIAGNOSTICS, mock(ScheduledExecutorService.class));
        }

        @Test
        void agentWithinSensorRadius_marksTargetFound() {
            Vector3D targetPosition = new Vector3D(50, -50, 25);
            detectionWorld.setTarget(new fr.gakkel.swarmsimulator.swarmserver.domain.Target(targetPosition));
            Agent nearAgent = createAgent(new Vector3D(50 + SimulationConstants.SENSOR_RADIUS_M - 1, -50, 25));
            detectionWorld.addAgent(nearAgent);

            detectionLoop.tick();

            assertTrue(detectionWorld.target().isFound());
            assertEquals(nearAgent.id().toString(), detectionWorld.target().foundByAgentId());
        }

        @Test
        void agentBeyondSensorRadius_doesNotMarkFound() {
            Vector3D targetPosition = new Vector3D(50, -50, 25);
            detectionWorld.setTarget(new fr.gakkel.swarmsimulator.swarmserver.domain.Target(targetPosition));
            detectionWorld.addAgent(createAgent(new Vector3D(50 + SimulationConstants.SENSOR_RADIUS_M + 1, -50, 25)));

            detectionLoop.tick();

            assertFalse(detectionWorld.target().isFound());
        }

        @Test
        void agentExactlyAtSensorRadius_marksTargetFound() {
            Vector3D targetPosition = new Vector3D(50, -50, 25);
            detectionWorld.setTarget(new fr.gakkel.swarmsimulator.swarmserver.domain.Target(targetPosition));
            detectionWorld.addAgent(createAgent(new Vector3D(50 + SimulationConstants.SENSOR_RADIUS_M, -50, 25)));

            detectionLoop.tick();

            assertTrue(detectionWorld.target().isFound());
        }

        @Test
        void alreadyFoundTarget_notDetectedAgain() {
            var target = new fr.gakkel.swarmsimulator.swarmserver.domain.Target(new Vector3D(50, -50, 25));
            target.markFound("first-agent");
            detectionWorld.setTarget(target);
            Agent secondAgent = createAgent(new Vector3D(50, -50, 25));
            detectionWorld.addAgent(secondAgent);

            detectionLoop.tick();

            assertEquals("first-agent", detectionWorld.target().foundByAgentId());
        }

        @Test
        void noTarget_tickRunsWithoutError() {
            detectionWorld.addAgent(createAgent(new Vector3D(50, -50, 25)));

            assertDoesNotThrow(() -> detectionLoop.tick());
        }
    }

    private static Agent createAgent(Vector3D position) {
        return new Agent(UUID.randomUUID(), AgentType.EXPLORER, position, Vector3D.ZERO);
    }
}
