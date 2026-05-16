package fr.gakkel.swarmsimulator.swarmserver.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BoidsRulesTest {

    private static final double DELTA = 1e-9;
    private static final BoidsConfig DEFAULT_CONFIG = BoidsConfig.builder().build();

    private BoidsRules rules;
    private Agent agent;

    @BeforeEach
    void setUp() {
        rules = new BoidsRules(DEFAULT_CONFIG);
        agent = Agent.create(AgentType.EXPLORER, new Vector3D(0, 0, 0));
    }

    @Nested
    class Separation {

        @Test
        void noNeighbors_returnsZero() {
            assertEquals(Vector3D.ZERO, rules.separation(agent, List.of()));
        }

        @Test
        void coLocatedNeighbor_returnsZero() {
            var coLocated = Agent.create(AgentType.EXPLORER, new Vector3D(0, 0, 0));

            assertEquals(Vector3D.ZERO, rules.separation(agent, List.of(coLocated)));
        }

        @Test
        void oneNeighbor_returnsVectorAwayFromNeighbor() {
            var neighbor = Agent.create(AgentType.EXPLORER, new Vector3D(5, 0, 0));

            var result = rules.separation(agent, List.of(neighbor));

            assertEquals(-1.0, result.x(), DELTA);
            assertEquals(0.0,  result.y(), DELTA);
            assertEquals(0.0,  result.z(), DELTA);
        }

        @Test
        void twoSymmetricNeighbors_forcesCancel() {
            var left  = Agent.create(AgentType.EXPLORER, new Vector3D(-5, 0, 0));
            var right = Agent.create(AgentType.EXPLORER, new Vector3D( 5, 0, 0));

            var result = rules.separation(agent, List.of(left, right));

            assertEquals(Vector3D.ZERO, result);
        }

        @Test
        void twoAgentsTooClose_generateOpposingForces() {
            // A at (0,0,0), B at (5,0,0) → A pushed -x, B pushed +x
            var a = new Agent(UUID.randomUUID(), AgentType.EXPLORER, new Vector3D(0, 0, 0), Vector3D.ZERO);
            var b = new Agent(UUID.randomUUID(), AgentType.EXPLORER, new Vector3D(5, 0, 0), Vector3D.ZERO);

            var forceOnA = rules.separation(a, List.of(b));
            var forceOnB = rules.separation(b, List.of(a));

            assertEquals(-1.0, forceOnA.x(), DELTA);
            assertEquals( 1.0, forceOnB.x(), DELTA);
        }
    }

    @Nested
    class Alignment {

        @Test
        void noNeighbors_returnsZero() {
            assertEquals(Vector3D.ZERO, rules.alignment(List.of()));
        }

        @Test
        void neighborsWithSameVelocity_returnsThatDirection() {
            var n1 = Agent.create(AgentType.EXPLORER, new Vector3D(5, 0, 0));
            n1.update(n1.position(), new Vector3D(3, 0, 0));
            var n2 = Agent.create(AgentType.EXPLORER, new Vector3D(-5, 0, 0));
            n2.update(n2.position(), new Vector3D(3, 0, 0));

            var result = rules.alignment(List.of(n1, n2));

            assertEquals(1.0, result.x(), DELTA);
            assertEquals(0.0, result.y(), DELTA);
            assertEquals(0.0, result.z(), DELTA);
        }

        @Test
        void neighborsWithOppositeVelocities_forcesCancel() {
            var n1 = Agent.create(AgentType.EXPLORER, new Vector3D(5, 0, 0));
            n1.update(n1.position(), new Vector3D(1, 0, 0));
            var n2 = Agent.create(AgentType.EXPLORER, new Vector3D(-5, 0, 0));
            n2.update(n2.position(), new Vector3D(-1, 0, 0));

            var result = rules.alignment(List.of(n1, n2));

            assertEquals(Vector3D.ZERO, result);
        }
    }

    @Nested
    class Cohesion {

        @Test
        void noNeighbors_returnsZero() {
            assertEquals(Vector3D.ZERO, rules.cohesion(agent, List.of()));
        }

        @Test
        void oneNeighbor_returnsDirectionTowardNeighbor() {
            var neighbor = Agent.create(AgentType.EXPLORER, new Vector3D(10, 0, 0));

            var result = rules.cohesion(agent, List.of(neighbor));

            assertEquals(1.0, result.x(), DELTA);
            assertEquals(0.0, result.y(), DELTA);
            assertEquals(0.0, result.z(), DELTA);
        }

        @Test
        void twoNeighbors_returnsDirectionTowardCentroid() {
            var n1 = Agent.create(AgentType.EXPLORER, new Vector3D(4, 0, 0));
            var n2 = Agent.create(AgentType.EXPLORER, new Vector3D(6, 0, 0));

            var result = rules.cohesion(agent, List.of(n1, n2));

            assertEquals(1.0, result.x(), DELTA);
            assertEquals(0.0, result.y(), DELTA);
            assertEquals(0.0, result.z(), DELTA);
        }

        @Test
        void threeAgentsInLine_outerAgentPulledTowardSwarmCenter() {
            // agent A at (0,0,0), neighbours B(10,0,0) and C(20,0,0)
            // centroid of neighbours = (15,0,0) → cohesion force points +x
            var b = Agent.create(AgentType.EXPLORER, new Vector3D(10, 0, 0));
            var c = Agent.create(AgentType.EXPLORER, new Vector3D(20, 0, 0));

            var result = rules.cohesion(agent, List.of(b, c));

            assertEquals(1.0, result.x(), DELTA);
            assertEquals(0.0, result.y(), DELTA);
            assertEquals(0.0, result.z(), DELTA);
        }
    }

    @Nested
    class Steer {

        @Test
        void onlySeparationActive_equalsWeightedSeparation() {
            var config = BoidsConfig.builder().separationWeight(2.0).alignmentWeight(0.0).cohesionWeight(0.0).boundaryRepulsionWeight(0.0).build();
            var separationOnlyRules = new BoidsRules(config);
            var neighbor = Agent.create(AgentType.EXPLORER, new Vector3D(5, 0, 0));

            var steer = separationOnlyRules.steer(agent, List.of(neighbor));
            var expected = separationOnlyRules.separation(agent, List.of(neighbor)).scale(2.0);

            assertEquals(expected.x(), steer.x(), DELTA);
            assertEquals(expected.y(), steer.y(), DELTA);
            assertEquals(expected.z(), steer.z(), DELTA);
        }

        @Test
        void allRulesActive_producesExpectedSteeringVector() {
            // agent at origin, neighbor at (5,0,0) moving up (0,1,0)
            // separation → (-1,0,0)*1.8 = (-1.8, 0,0)
            // alignment  → ( 0,1,0)*1.5 = ( 0.0,1.5,0)
            // cohesion   → ( 1,0,0)*1.5 = ( 1.5, 0,0)
            // steer      → (-0.3, 1.5, 0.0)
            var neighbor = Agent.create(AgentType.EXPLORER, new Vector3D(5, 0, 0));
            neighbor.update(neighbor.position(), new Vector3D(0, 1, 0));

            var steer = rules.steer(agent, List.of(neighbor));

            assertEquals(-0.3, steer.x(), DELTA);
            assertEquals( 1.5, steer.y(), DELTA);
            assertEquals( 0.0, steer.z(), DELTA);
        }
    }

    @Nested
    class BoundaryRepulsion {

        // world is recreated per test — no shared mutable state between sibling tests
        private World world;

        @org.junit.jupiter.api.BeforeEach
        void setUp() { world = new World(100, 100, 50); }

        @Test
        void farFromWalls_returnsZero() {
            var a = new Agent(UUID.randomUUID(), AgentType.EXPLORER, new Vector3D(50, 50, 25), Vector3D.ZERO);
            assertEquals(Vector3D.ZERO, rules.boundaryRepulsion(a, world));
        }

        @Test
        void nearXMinWall_forcePushesPositiveX() {
            var a = new Agent(UUID.randomUUID(), AgentType.EXPLORER, new Vector3D(5, 50, 25), Vector3D.ZERO);
            var force = rules.boundaryRepulsion(a, world);
            assertTrue(force.x() > 0, "force should push away from x=0 wall");
            assertEquals(0.0, force.y(), DELTA);
            assertEquals(0.0, force.z(), DELTA);
        }

        @Test
        void atExactRadiusBoundary_returnsZero() {
            var a = new Agent(UUID.randomUUID(), AgentType.EXPLORER,
                    new Vector3D(DEFAULT_CONFIG.boundaryRepulsionRadius(), 50, 25), Vector3D.ZERO);
            assertEquals(Vector3D.ZERO, rules.boundaryRepulsion(a, world));
        }

        @Test
        void nearXMaxWall_forcePushesNegativeX_andYZAreZero() {
            var a = new Agent(UUID.randomUUID(), AgentType.EXPLORER, new Vector3D(95, 50, 25), Vector3D.ZERO);
            var force = rules.boundaryRepulsion(a, world);
            assertTrue(force.x() < 0, "force should push away from x=width wall");
            assertEquals(0.0, force.y(), DELTA);
            assertEquals(0.0, force.z(), DELTA);
        }
    }

    @Nested
    class ObstacleAvoidance {

        @Test
        void emptyObstacles_returnsZero() {
            assertEquals(Vector3D.ZERO, rules.obstacleAvoidance(agent, List.of()));
        }

        @Test
        void agentBeyondAvoidanceRadius_returnsZero() {
            // avoidanceRadius=8, obstacle r=2 at (20,0,0), agent at (0,0,0) → gap=18 > 8 → no force
            var obstacle = new Obstacle(new Vector3D(20, 0, 0), 2.0);

            assertEquals(Vector3D.ZERO, rules.obstacleAvoidance(agent, List.of(obstacle)));
        }

        @Test
        void agentInsideAvoidanceRadius_pushesAwayFromObstacleCenter() {
            // obstacle r=2 at (5,0,0), agent at (0,0,0): gap=3, force points in -x direction
            var obstacle = new Obstacle(new Vector3D(5, 0, 0), 2.0);

            var force = rules.obstacleAvoidance(agent, List.of(obstacle));

            assertTrue(force.x() < 0, "force should push agent away from obstacle along -x");
            assertEquals(0.0, force.y(), DELTA);
            assertEquals(0.0, force.z(), DELTA);
        }

        @Test
        void closerAgentReceivesStrongerForce() {
            // obstacle r=2 at x=10 → surface at x=8 (left side)
            // farAgent at x=4 → 4u from the surface; nearAgent at x=7 → 1u from the surface
            // "far"/"near" labels refer to distance from the OBSTACLE SURFACE, not from the origin
            var obstacle = new Obstacle(new Vector3D(10, 0, 0), 2.0);
            var farAgent  = new Agent(UUID.randomUUID(), AgentType.EXPLORER, new Vector3D(4, 0, 0), Vector3D.ZERO);
            var nearAgent = new Agent(UUID.randomUUID(), AgentType.EXPLORER, new Vector3D(7, 0, 0), Vector3D.ZERO);

            double farMag  = rules.obstacleAvoidance(farAgent,  List.of(obstacle)).magnitude();
            double nearMag = rules.obstacleAvoidance(nearAgent, List.of(obstacle)).magnitude();

            assertTrue(nearMag > farMag,
                    "agent closer to obstacle must receive stronger force (near=" + nearMag + " far=" + farMag + ")");
        }

        @Test
        void atSurface_strengthIsOne() {
            // obstacle r=2 at (5,0,0), agent at (3,0,0) → distance=2, gap=0, t=0, exp(0)=1.0
            var obstacle = new Obstacle(new Vector3D(5, 0, 0), 2.0);
            var surfaceAgent = new Agent(UUID.randomUUID(), AgentType.EXPLORER, new Vector3D(3, 0, 0), Vector3D.ZERO);

            var force = rules.obstacleAvoidance(surfaceAgent, List.of(obstacle));

            assertEquals(1.0, force.magnitude(), DELTA);
            assertEquals(-1.0, force.x(), DELTA);
        }

        @Test
        void agentInsideObstacle_strengthSaturatesToOne() {
            // obstacle r=5 at (10,0,0), agent at (12,0,0) → distance=2, gap=-3 → t=max(-3,0)/8=0, strength=1
            var obstacle = new Obstacle(new Vector3D(10, 0, 0), 5.0);
            var insideAgent = new Agent(UUID.randomUUID(), AgentType.EXPLORER, new Vector3D(12, 0, 0), Vector3D.ZERO);

            var force = rules.obstacleAvoidance(insideAgent, List.of(obstacle));

            assertEquals(1.0, force.magnitude(), DELTA);
            assertTrue(force.x() > 0, "agent inside obstacle should be pushed outward along +x");
        }

        @Test
        void agentAtObstacleCenter_returnsZero() {
            // degenerate: no defined direction, skip rather than NaN
            var obstacle = new Obstacle(new Vector3D(0, 0, 0), 2.0);

            assertEquals(Vector3D.ZERO, rules.obstacleAvoidance(agent, List.of(obstacle)));
        }

        @Test
        void twoSymmetricObstacles_forcesCancel() {
            var left  = new Obstacle(new Vector3D(-5, 0, 0), 1.0);
            var right = new Obstacle(new Vector3D( 5, 0, 0), 1.0);

            var force = rules.obstacleAvoidance(agent, List.of(left, right));

            assertEquals(0.0, force.x(), DELTA);
            assertEquals(0.0, force.y(), DELTA);
            assertEquals(0.0, force.z(), DELTA);
        }
    }
}
