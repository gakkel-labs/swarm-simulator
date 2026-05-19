package fr.gakkel.swarmsimulator.swarmserver.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        var agent = Agent.create(AgentType.EXPLORER, new Vector3D(20, -50, 25));
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
        var agent = Agent.create(AgentType.EXPLORER, new Vector3D(10 + Predator.EAT_RADIUS * 0.5, -50, 25));
        world.addAgent(agent);

        Agent eaten = predator.tryEat(world);

        assertSame(agent, eaten);
        assertTrue(world.agents().isEmpty());
    }

    @Test
    void predatorTryEat_whenOutsideEatRadius_returnsNull() {
        var predator = new Predator(new Vector3D(10, -50, 25), Vector3D.ZERO);
        var agent = Agent.create(AgentType.EXPLORER, new Vector3D(10 + Predator.EAT_RADIUS * 2, -50, 25));
        world.addAgent(agent);

        Agent eaten = predator.tryEat(world);

        assertNull(eaten);
        assertEquals(1, world.agentCount());
    }
}
