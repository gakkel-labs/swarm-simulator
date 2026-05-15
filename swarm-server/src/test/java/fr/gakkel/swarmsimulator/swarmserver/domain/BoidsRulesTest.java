package fr.gakkel.swarmsimulator.swarmserver.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BoidsRulesTest {

    private static final double DELTA = 1e-9;
    private static final BoidsConfig DEFAULT_CONFIG = new BoidsConfig(15.0, 1.5, 1.0, 1.0, 5.0, 15.0, 2.0);

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
    }

    @Nested
    class Steer {

        @Test
        void onlySeparationActive_equalsWeightedSeparation() {
            var config = new BoidsConfig(15.0, 2.0, 0.0, 0.0, 5.0, 15.0, 0.0);
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
            // separation → (-1,0,0)*1.5 = (-1.5,0,0)
            // alignment  → ( 0,1,0)*1.0 = ( 0.0,1,0)
            // cohesion   → ( 1,0,0)*1.0 = ( 1.0,0,0)
            // steer      → (-0.5, 1.0, 0.0)
            var neighbor = Agent.create(AgentType.EXPLORER, new Vector3D(5, 0, 0));
            neighbor.update(neighbor.position(), new Vector3D(0, 1, 0));

            var steer = rules.steer(agent, List.of(neighbor));

            assertEquals(-0.5, steer.x(), DELTA);
            assertEquals(1.0,  steer.y(), DELTA);
            assertEquals(0.0,  steer.z(), DELTA);
        }
    }

    @Nested
    class BoundaryRepulsion {

        private static final World WORLD = new World(100, 100, 50);

        @Test
        void farFromWalls_returnsZero() {
            var a = new Agent(UUID.randomUUID(), AgentType.EXPLORER, new Vector3D(50, 50, 25), Vector3D.ZERO);
            assertEquals(Vector3D.ZERO, rules.boundaryRepulsion(a, WORLD));
        }

        @Test
        void nearXMinWall_forcePushesPositiveX() {
            var a = new Agent(UUID.randomUUID(), AgentType.EXPLORER, new Vector3D(5, 50, 25), Vector3D.ZERO);
            var force = rules.boundaryRepulsion(a, WORLD);
            assertTrue(force.x() > 0, "force should push away from x=0 wall");
            assertEquals(0.0, force.y(), DELTA);
            assertEquals(0.0, force.z(), DELTA);
        }

        @Test
        void nearXMaxWall_forcePushesNegativeX() {
            var a = new Agent(UUID.randomUUID(), AgentType.EXPLORER, new Vector3D(95, 50, 25), Vector3D.ZERO);
            var force = rules.boundaryRepulsion(a, WORLD);
            assertTrue(force.x() < 0, "force should push away from x=width wall");
        }

        @Test
        void atExactRadiusBoundary_returnsZero() {
            var a = new Agent(UUID.randomUUID(), AgentType.EXPLORER,
                    new Vector3D(DEFAULT_CONFIG.boundaryRepulsionRadius(), 50, 25), Vector3D.ZERO);
            assertEquals(Vector3D.ZERO, rules.boundaryRepulsion(a, WORLD));
        }
    }
}
