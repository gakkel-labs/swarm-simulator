package fr.gakkel.swarmsimulator.swarmserver.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PredatorTest {

    private World world;

    @BeforeEach
    void setUp() {
        world = new World(100, 100, 50);
    }

    @Test
    void predatorUpdate_movesTowardNearestAgent() {
        var predator = new Predator(new Vector3D(10, -50, 25), Vector3D.ZERO);
        var agent = Agent.create(TestDrones.DEMO_DETECTION_DRONE, new Vector3D(20, -50, 25));
        world.addAgent(agent);

        predator.update(world, 1.0);

        assertTrue(predator.position().x() > 10, "predator should have moved toward agent (x+)");
        assertEquals(-50, predator.position().y(), 1e-9);
        assertEquals(25,  predator.position().z(), 1e-9);
    }

    @Test
    void predatorUpdate_noAgents_doesNotMove() {
        var initial = new Vector3D(10, -50, 25);
        var predator = new Predator(initial, Vector3D.ZERO);

        predator.update(world, 1.0);

        assertEquals(initial, predator.position());
    }

    @Test
    void predatorTryEat_whenInEatRadius_removesAgentFromWorld() {
        var predator = new Predator(new Vector3D(10, -50, 25), Vector3D.ZERO);
        double eatRadius = PredatorConfig.defaults().eatRadius();
        var agent = Agent.create(TestDrones.DEMO_DETECTION_DRONE, new Vector3D(10 + eatRadius * 0.5, -50, 25));
        world.addAgent(agent);

        Agent eaten = predator.tryEat(world);

        assertSame(agent, eaten);
        assertTrue(world.agents().isEmpty());
    }

    @Test
    void predatorTryEat_whenOutsideEatRadius_returnsNull() {
        var predator = new Predator(new Vector3D(10, -50, 25), Vector3D.ZERO);
        double eatRadius = PredatorConfig.defaults().eatRadius();
        var agent = Agent.create(TestDrones.DEMO_DETECTION_DRONE, new Vector3D(10 + eatRadius * 2, -50, 25));
        world.addAgent(agent);

        Agent eaten = predator.tryEat(world);

        assertNull(eaten);
        assertEquals(1, world.agentCount());
    }

    @Test
    void predatorUpdate_movesAtTheConfiguredSpeed() {
        var predator = new Predator(UUID.randomUUID(), new Vector3D(10, -50, 25), Vector3D.ZERO,
                new PredatorConfig(2.0, 1.5));
        world.addAgent(Agent.create(TestDrones.DEMO_DETECTION_DRONE, new Vector3D(40, -50, 25)));

        predator.update(world, 1.0);

        assertEquals(12, predator.position().x(), 1e-9);
        assertEquals(2.0, predator.velocity().magnitude(), 1e-9);
    }

    @Test
    void predatorTryEat_usesTheConfiguredEatRadius() {
        var predator = new Predator(UUID.randomUUID(), new Vector3D(10, -50, 25), Vector3D.ZERO,
                new PredatorConfig(3.0, 5.0));
        world.addAgent(Agent.create(TestDrones.DEMO_DETECTION_DRONE, new Vector3D(14, -50, 25)));

        Agent eaten = predator.tryEat(world);

        assertNotNull(eaten);
    }

    @Test
    void predatorUpdate_motionGivesPositionAndVelocityOfTheSameStep() {
        var predator = new Predator(new Vector3D(10, -50, 25), Vector3D.ZERO);
        world.addAgent(Agent.create(TestDrones.DEMO_DETECTION_DRONE, new Vector3D(20, -50, 25)));

        predator.update(world, 1.0);
        Motion motion = predator.motion();

        assertEquals(predator.position(), motion.position());
        assertEquals(predator.velocity(), motion.velocity());
    }

    @Test
    void predatorConfig_nonPositiveValues_throw() {
        assertThrows(IllegalArgumentException.class, () -> new PredatorConfig(0, 1.5));
        assertThrows(IllegalArgumentException.class, () -> new PredatorConfig(3.0, -1));
    }

    @Test
    void predator_eachPredatorHasItsOwnId() {
        var first = new Predator(new Vector3D(10, -50, 25), Vector3D.ZERO);
        var second = new Predator(new Vector3D(10, -50, 25), Vector3D.ZERO);

        assertNotEquals(first.id(), second.id());
    }
}
