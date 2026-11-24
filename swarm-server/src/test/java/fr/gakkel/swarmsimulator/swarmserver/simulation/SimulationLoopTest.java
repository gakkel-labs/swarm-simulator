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
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;

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
    void createWorld_scalesObstacleRadiiWithSmallestDimensionRatio() {
        // default dimensions => scale 1.0 => original radii (8/5/6) unchanged
        World defaultWorld = SimulationLoop.createWorld(5, 100, 100, 50, new Random(0L));
        assertEquals(8.0, defaultWorld.obstacles().get(0).radius(), 1e-9);

        // half the depth (the smallest extent) => radii halved so spheres still fit the box
        World smallWorld = SimulationLoop.createWorld(5, 100, 100, 25, new Random(0L));
        assertEquals(4.0, smallWorld.obstacles().get(0).radius(), 1e-9);
    }

    @Test
    void tick_movesTheSimulationForwardByOneThirtiethOfASecond() {
        Simulation simulation = new Simulation(world, CONFIG, new Random(1L), new Random(2L));
        var liveLoop = new SimulationLoop(simulation, CONFIG, DIAGNOSTICS,
                mock(ScheduledExecutorService.class), null);

        for (int tick = 0; tick < SimulationLoop.TICK_RATE_HZ; tick++) liveLoop.tick();

        assertEquals(1.0, simulation.clock().elapsedSeconds(), 1e-9);
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
    void createDefaultWorld_hasPredator() {
        assertNotNull(SimulationLoop.createDefaultWorld().predator());
    }

    private static Agent createAgent(Vector3D position) {
        return new Agent(UUID.randomUUID(), AgentType.EXPLORER, position, Vector3D.ZERO);
    }

    @Nested
    class Robustness {

        @Test
        void tick_withEmptySwarm_keepsTheLastSpreadInsteadOfNaN() {
            // All drones can be eaten by the predator: the swarm is empty until they respawn.
            // Design choice: an empty swarm has no spread to measure, so the last value is kept.
            loop.tick();
            double spreadWithDrones = loop.cohesionSpreadM();
            for (Agent agent : List.copyOf(world.agents())) world.removeAgent(agent);

            loop.tick();

            assertTrue(spreadWithDrones > 0, "the swarm of 10 drones has a spread");
            assertEquals(spreadWithDrones, loop.cohesionSpreadM(), 1e-12);
        }

        @Test
        void scheduledTick_whenTickThrows_doesNotPropagateTheException() {
            // scheduleAtFixedRate stops a task for good, with no log, if it throws once.
            CohesionCsvExporter failingExporter = mock(CohesionCsvExporter.class);
            doThrow(new IllegalStateException("disk full")).when(failingExporter)
                    .record(anyLong(), anyDouble(), anyDouble());
            ScheduledExecutorService mockExecutor = mock(ScheduledExecutorService.class);
            var testLoop = new SimulationLoop(world, CONFIG, DIAGNOSTICS, mockExecutor, failingExporter);
            ArgumentCaptor<Runnable> tickCaptor = ArgumentCaptor.forClass(Runnable.class);
            testLoop.start();
            verify(mockExecutor).scheduleAtFixedRate(tickCaptor.capture(), anyLong(), anyLong(), any());
            Runnable scheduledTick = tickCaptor.getValue();

            assertDoesNotThrow(() -> {
                for (int tick = 0; tick < SimulationLoop.TICK_RATE_HZ; tick++) scheduledTick.run();
            });
        }
    }
}
