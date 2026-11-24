package fr.gakkel.swarmsimulator.swarmserver.simulation;

import fr.gakkel.swarmsimulator.swarmserver.domain.Agent;
import fr.gakkel.swarmsimulator.swarmserver.domain.DroneGroup;
import fr.gakkel.swarmsimulator.swarmserver.domain.DroneSpecification;
import fr.gakkel.swarmsimulator.swarmserver.domain.TestDrones;
import fr.gakkel.swarmsimulator.swarmserver.domain.World;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorldFactoryTest {

    @Test
    void createWorld_twentyDemoDrones_creates20Agents() {
        assertEquals(20, demoWorld().agentCount());
    }

    @Test
    void createWorld_twoModels_createsEachGroupInOrderWithItsSpecification() {
        DroneSpecification sideScan = TestDrones.detectionDrone("side-scan-a", 1.5, 50.0);
        DroneSpecification fast = TestDrones.detectionDrone("fast-b", 2.5, 30.0);
        List<DroneGroup> groups = List.of(new DroneGroup(sideScan, 2), new DroneGroup(fast, 1));

        World world = WorldFactory.createWorld(groups, new Random(0L));

        assertEquals(2, world.agents().stream()
                .filter(agent -> agent.specification().equals(sideScan)).count());
        assertEquals(1, world.agents().stream()
                .filter(agent -> agent.specification().equals(fast)).count());
    }

    @Test
    void createWorld_sameGroupsAndSeed_givesTheSameDrones() {
        World first = demoWorld();
        World second = demoWorld();

        assertEquals(first.agents().stream().map(Agent::position).toList(),
                second.agents().stream().map(Agent::position).toList());
    }

    @Test
    void createWorld_demoDrones_agentsStartInBounds() {
        World defaultWorld = demoWorld();
        for (Agent agent : defaultWorld.agents()) {
            assertTrue(defaultWorld.isInBounds(agent.position()), "agent out of bounds: " + agent.position());
        }
    }

    @Test
    void createWorld_scalesObstacleRadiiWithSmallestDimensionRatio() {
        // default dimensions => scale 1.0 => original radii (8/5/6) unchanged
        World defaultWorld = WorldFactory.createWorld(TestDrones.demoDrones(5), 100, 100, 50, new Random(0L));
        assertEquals(8.0, defaultWorld.obstacles().get(0).radius(), 1e-9);

        // half the depth (the smallest extent) => radii halved so spheres still fit the box
        World smallWorld = WorldFactory.createWorld(TestDrones.demoDrones(5), 100, 100, 25, new Random(0L));
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
    void createWorld_demoDrones_hasPredator() {
        assertNotNull(demoWorld().predator());
    }

    private static World demoWorld() {
        return WorldFactory.createWorld(TestDrones.demoDrones(20), new Random(42L));
    }
}
