package fr.gakkel.swarmsimulator.swarmserver.simulation;

import fr.gakkel.swarmsimulator.swarmserver.domain.Agent;
import fr.gakkel.swarmsimulator.swarmserver.domain.World;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorldFactoryTest {

    @Test
    void createDefaultWorld_creates20Agents() {
        assertEquals(20, WorldFactory.createDefaultWorld().agentCount());
    }

    @Test
    void createDefaultWorld_agentsStartInBounds() {
        World defaultWorld = WorldFactory.createDefaultWorld();
        for (Agent agent : defaultWorld.agents()) {
            assertTrue(defaultWorld.isInBounds(agent.position()), "agent out of bounds: " + agent.position());
        }
    }

    @Test
    void createWorld_scalesObstacleRadiiWithSmallestDimensionRatio() {
        // default dimensions => scale 1.0 => original radii (8/5/6) unchanged
        World defaultWorld = WorldFactory.createWorld(5, 100, 100, 50, new Random(0L));
        assertEquals(8.0, defaultWorld.obstacles().get(0).radius(), 1e-9);

        // half the depth (the smallest extent) => radii halved so spheres still fit the box
        World smallWorld = WorldFactory.createWorld(5, 100, 100, 25, new Random(0L));
        assertEquals(4.0, smallWorld.obstacles().get(0).radius(), 1e-9);
    }

    @Test
    void seededId_sameSeed_givesTheSameVersionFourId() {
        UUID firstId = WorldFactory.seededId(new Random(5L));
        UUID secondId = WorldFactory.seededId(new Random(5L));

        assertEquals(firstId, secondId);
        assertEquals(4, firstId.version());
        assertEquals(2, firstId.variant());
    }

    @Test
    void createDefaultWorld_hasPredator() {
        assertNotNull(WorldFactory.createDefaultWorld().predator());
    }
}
