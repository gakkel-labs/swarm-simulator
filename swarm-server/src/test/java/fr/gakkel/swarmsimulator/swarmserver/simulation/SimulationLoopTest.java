package fr.gakkel.swarmsimulator.swarmserver.simulation;


import fr.gakkel.swarmsimulator.swarmserver.domain.Agent;
import fr.gakkel.swarmsimulator.swarmserver.domain.AgentType;
import fr.gakkel.swarmsimulator.swarmserver.domain.BoidsConfig;
import fr.gakkel.swarmsimulator.swarmserver.domain.Obstacle;
import fr.gakkel.swarmsimulator.swarmserver.domain.Vector3D;
import fr.gakkel.swarmsimulator.swarmserver.domain.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SimulationLoopTest {

    private static final BoidsConfig CONFIG = BoidsConfig.builder().build();
    private static final DiagnosticsConfig DIAGNOSTICS = DiagnosticsConfig.builder().build();

    private World world;
    private SimulationLoop loop;

    @BeforeEach
    void setUp() {
        world = SimulationLoop.createWorld(10, new Random(0L));
        loop = new SimulationLoop(world, CONFIG, DIAGNOSTICS);
    }

    @Test
    void createDefaultWorld_creates20Agents() {
        assertEquals(20, SimulationLoop.createDefaultWorld().agentCount());
    }

    @Test
    void createDefaultWorld_agentsStartInBounds() {
        World w = SimulationLoop.createDefaultWorld();
        for (Agent a : w.agents()) {
            assertTrue(w.isInBounds(a.position()), "agent out of bounds: " + a.position());
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
        Vector3D v = new Vector3D(1, 0, 0);
        assertEquals(v, SimulationLoop.clampSpeed(v, 5.0));
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
            var a = agent(new Vector3D(10, 20, 30));
            assertEquals(new Vector3D(10, 20, 30), SimulationLoop.computeCentroid(List.of(a)));
        }

        @Test
        void twoSymmetricAgents_centroidIsOrigin() {
            var a1 = agent(new Vector3D(-5, 0, 0));
            var a2 = agent(new Vector3D(5, 0, 0));
            assertEquals(new Vector3D(0, 0, 0), SimulationLoop.computeCentroid(List.of(a1, a2)));
        }
    }

    @Nested
    class ComputePositionStandardDeviation {

        @Test
        void allAgentsAtSamePosition_sigmaIsZero() {
            var a1 = agent(new Vector3D(5, 5, 5));
            var a2 = agent(new Vector3D(5, 5, 5));
            Vector3D centroid = SimulationLoop.computeCentroid(List.of(a1, a2));
            assertEquals(0.0, SimulationLoop.computePositionStandardDeviation(List.of(a1, a2), centroid), 1e-9);
        }

        @Test
        void twoAgentsSymmetric_sigmaEqualsDistanceToCentroid() {
            // each agent is 5u from centroid → σ = sqrt((5² + 5²) / 2) = 5
            var a1 = agent(new Vector3D(0, 0, 0));
            var a2 = agent(new Vector3D(10, 0, 0));
            Vector3D centroid = SimulationLoop.computeCentroid(List.of(a1, a2));
            assertEquals(5.0, SimulationLoop.computePositionStandardDeviation(List.of(a1, a2), centroid), 1e-9);
        }
    }

    @Nested
    class MinObstacleGap {

        @Test
        void noObstacles_returnsPositiveInfinity() {
            var a = agent(new Vector3D(50, 50, 25));

            double gap = SimulationLoop.minObstacleGap(List.of(a), List.of());

            assertEquals(Double.POSITIVE_INFINITY, gap);
        }

        @Test
        void oneAgentOneObstacle_returnsDistanceToSurface() {
            var a = agent(new Vector3D(0, 0, 0));
            var obstacle = new Obstacle(new Vector3D(10, 0, 0), 3.0);

            double gap = SimulationLoop.minObstacleGap(List.of(a), List.of(obstacle));

            assertEquals(7.0, gap, 1e-9);  // 10 - 3 = 7
        }

        @Test
        void picksSmallestGapAcrossAllPairs() {
            var a1 = agent(new Vector3D(0, 0, 0));
            var a2 = agent(new Vector3D(100, 0, 0));
            var farObstacle  = new Obstacle(new Vector3D(50, 0, 0), 1.0);   // gap from a1 = 49
            var nearObstacle = new Obstacle(new Vector3D(5, 0, 0), 2.0);    // gap from a1 = 3 ← min

            double gap = SimulationLoop.minObstacleGap(List.of(a1, a2), List.of(farObstacle, nearObstacle));

            assertEquals(3.0, gap, 1e-9);
        }

        @Test
        void agentInsideObstacle_returnsNegativeGap() {
            var a = agent(new Vector3D(10, 0, 0));
            var obstacle = new Obstacle(new Vector3D(10, 0, 0), 5.0);  // agent at center

            double gap = SimulationLoop.minObstacleGap(List.of(a), List.of(obstacle));

            assertEquals(-5.0, gap, 1e-9);
        }
    }

    @Nested
    class CountAgentsInsideObstacles {

        @Test
        void noPenetration_returnsZero() {
            var outside = agent(new Vector3D(0, 0, 0));
            var obstacle = new Obstacle(new Vector3D(50, 50, 25), 5.0);

            assertEquals(0, SimulationLoop.countAgentsInsideObstacles(List.of(outside), List.of(obstacle)));
        }

        @Test
        void oneInside_returnsOne() {
            var inside  = agent(new Vector3D(11, 0, 0));  // 1u from center, radius 3 → inside
            var outside = agent(new Vector3D(50, 0, 0));
            var obstacle = new Obstacle(new Vector3D(10, 0, 0), 3.0);

            long count = SimulationLoop.countAgentsInsideObstacles(List.of(inside, outside), List.of(obstacle));

            assertEquals(1, count);
        }

        @Test
        void multipleObstacles_agentCountedOnceEvenIfInsideSeveral() {
            var a = agent(new Vector3D(10, 0, 0));
            var o1 = new Obstacle(new Vector3D(10, 0, 0), 3.0);  // contains a
            var o2 = new Obstacle(new Vector3D(11, 0, 0), 3.0);  // also contains a

            long count = SimulationLoop.countAgentsInsideObstacles(List.of(a), List.of(o1, o2));

            assertEquals(1, count);
        }
    }

    private static Agent agent(Vector3D position) {
        return new Agent(UUID.randomUUID(), AgentType.EXPLORER, position, Vector3D.ZERO);
    }
}
