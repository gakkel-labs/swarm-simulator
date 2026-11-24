package fr.gakkel.swarmsimulator.swarmserver.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class WorldTest {

    private World world;

    @BeforeEach
    void setUp() {
        world = new World(100, 100, 50);
    }

    @Test
    void addAgent_agentAppearsInAgentsList() {
        var agent = Agent.create(AgentType.EXPLORER, new Vector3D(10, 10, 5));

        world.addAgent(agent);

        assertTrue(world.agents().contains(agent));
    }

    @Test
    void addAgent_duplicateAgent_throwsIllegalArgumentException() {
        var agent = Agent.create(AgentType.EXPLORER, new Vector3D(10, 10, 5));
        world.addAgent(agent);

        assertThrows(IllegalArgumentException.class, () -> world.addAgent(agent));
    }

    @Test
    void removeAgent_presentAgent_removesAndReturnsTrue() {
        var agent = Agent.create(AgentType.SPECIALIST, new Vector3D(5, 5, 5));
        world.addAgent(agent);

        boolean removed = world.removeAgent(agent);

        assertTrue(removed);
        assertFalse(world.agents().contains(agent));
    }

    @Test
    void removeAgent_absentAgent_returnsFalse() {
        var agent = Agent.create(AgentType.EXPLORER, Vector3D.ZERO);

        boolean removed = world.removeAgent(agent);

        assertFalse(removed);
    }

    @Test
    void agentCount_reflectsAdditionsAndRemovals() {
        var a1 = Agent.create(AgentType.EXPLORER, Vector3D.ZERO);
        var a2 = Agent.create(AgentType.CARRIER, new Vector3D(1, 0, 0));
        world.addAgent(a1);
        world.addAgent(a2);

        assertEquals(2, world.agentCount());

        world.removeAgent(a1);
        assertEquals(1, world.agentCount());
    }

    @Test
    void neighbors_returnsAgentsWithinRadius_excludesSelf() {
        var center = Agent.create(AgentType.EXPLORER, new Vector3D(50, 50, 25));
        var near = Agent.create(AgentType.EXPLORER, new Vector3D(55, 50, 25));
        var far = Agent.create(AgentType.EXPLORER, new Vector3D(90, 50, 25));
        world.addAgent(center);
        world.addAgent(near);
        world.addAgent(far);

        var result = world.neighbors(center, 10.0);

        assertEquals(1, result.size());
        assertTrue(result.contains(near));
        assertFalse(result.contains(center));
        assertFalse(result.contains(far));
    }

    @Test
    void neighbors_radiusZero_returnsEmptyList() {
        var agent = Agent.create(AgentType.EXPLORER, new Vector3D(50, 50, 25));
        var other = Agent.create(AgentType.EXPLORER, new Vector3D(51, 50, 25));
        world.addAgent(agent);
        world.addAgent(other);

        assertTrue(world.neighbors(agent, 0.0).isEmpty());
    }

    @Test
    void neighbors_negativeRadius_throwsIllegalArgumentException() {
        var agent = Agent.create(AgentType.EXPLORER, new Vector3D(50, 50, 25));
        world.addAgent(agent);

        assertThrows(IllegalArgumentException.class, () -> world.neighbors(agent, -1.0));
    }

    @Test
    void isInBounds_positionInsideWorld_returnsTrue() {
        // Y=0 is surface, Y=-100 is sea floor; Y=-50 is valid underwater
        assertTrue(world.isInBounds(new Vector3D(50, -50, 25)));
    }

    @Test
    void isInBounds_positionOnBoundary_returnsTrue() {
        assertTrue(world.isInBounds(new Vector3D(0, 0, 0)));           // surface corner
        assertTrue(world.isInBounds(new Vector3D(100, -100, 50)));     // sea floor corner
    }

    @Test
    void isInBounds_positionOutsideWorld_returnsFalse() {
        assertFalse(world.isInBounds(new Vector3D(-1, -50, 25)));      // X out
        assertFalse(world.isInBounds(new Vector3D(50, 1, 25)));        // above surface
        assertFalse(world.isInBounds(new Vector3D(50, -101, 25)));     // below sea floor
        assertFalse(world.isInBounds(new Vector3D(50, -50, 51)));      // Z out
    }

    @Test
    void clamp_positionOutsideWorld_clampsToBoundary() {
        // below floor + outside horizontal → clamp to sea-floor corner
        assertEquals(new Vector3D(0, -100, 0), world.clamp(new Vector3D(-10, -105, -1)));
        // above surface + outside horizontal → clamp to surface corner
        assertEquals(new Vector3D(100, 0, 50), world.clamp(new Vector3D(200, 10, 99)));
    }

    @Test
    void constructor_negativeWidth_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new World(-1, 100, 50));
    }

    @Test
    void constructor_zeroHeight_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new World(100, 0, 50));
    }

    @Test
    void constructor_negativeDepth_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new World(100, 100, -1));
    }

    @Test
    void addObstacle_obstacleAppearsInObstaclesList() {
        var obstacle = new Obstacle(new Vector3D(50, 50, 25), 5.0);

        world.addObstacle(obstacle);

        assertTrue(world.obstacles().contains(obstacle));
        assertEquals(1, world.obstacles().size());
    }

    @Test
    @SuppressWarnings("DataFlowIssue")  // intentional: verify the view is truly unmodifiable
    void obstacles_returnsUnmodifiableView() {
        var obstacle = new Obstacle(new Vector3D(10, 10, 10), 2.0);
        var view = world.obstacles();

        assertThrows(UnsupportedOperationException.class, () -> view.add(obstacle));
    }

    @Test
    void addObstacle_nullObstacle_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> world.addObstacle(null));
    }

    @Test
    void predator_defaultIsNull() {
        assertNull(world.predator());
    }

    @Test
    void setPredator_predatorAppearsInPredatorField() {
        var predator = new Predator(new Vector3D(10, -50, 25), Vector3D.ZERO);

        world.setPredator(predator);

        assertSame(predator, world.predator());
    }

    @Test
    void setPredator_nullClearsPredator() {
        world.setPredator(new Predator(new Vector3D(10, -50, 25), Vector3D.ZERO));

        world.setPredator(null);

        assertNull(world.predator());
    }

    // The simulation thread adds and removes drones (predator eats, respawn) while the broadcast
    // thread reads world.agents() to build the WorldState. Reading must never fail.
    @Test
    void agents_droneAddedDuringIteration_iterationDoesNotFail() {
        world.addAgent(Agent.create(AgentType.EXPLORER, new Vector3D(10, -10, 5)));
        world.addAgent(Agent.create(AgentType.EXPLORER, new Vector3D(20, -20, 5)));

        assertDoesNotThrow(() -> {
            for (Agent ignored : world.agents()) {
                world.addAgent(Agent.create(AgentType.EXPLORER, new Vector3D(30, -30, 5)));
            }
        });
    }

    @Test
    void agents_readByOneThreadWhileAnotherAddsAndRemoves_neverFails() throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            world.addAgent(Agent.create(AgentType.EXPLORER, new Vector3D(i, -i, 5)));
        }
        var stopWriter = new AtomicBoolean(false);
        var writerStarted = new CountDownLatch(1);
        Thread writer = new Thread(() -> {
            writerStarted.countDown();
            while (!stopWriter.get()) {
                var drone = Agent.create(AgentType.EXPLORER, new Vector3D(1, -1, 1));
                world.addAgent(drone);
                world.removeAgent(drone);
            }
        }, "test-simulation-writer");
        var readerFailure = new AtomicReference<Throwable>();

        writer.start();
        assertTrue(writerStarted.await(1, TimeUnit.SECONDS));
        try {
            for (int round = 0; round < 20_000; round++) {
                for (Agent drone : world.agents()) {
                    drone.position();
                }
            }
        } catch (Throwable failure) {
            readerFailure.set(failure);
        } finally {
            stopWriter.set(true);
            writer.join();
        }

        assertNull(readerFailure.get(), () -> "reader failed: " + readerFailure.get());
    }

    @Test
    void agents_areKeptInIdOrder_whateverTheOrderTheyWereAdded() {
        World world = new World(100, 100, 50);
        Agent first  = new Agent(new UUID(0, 1), AgentType.EXPLORER, Vector3D.ZERO, Vector3D.ZERO);
        Agent second = new Agent(new UUID(0, 2), AgentType.EXPLORER, Vector3D.ZERO, Vector3D.ZERO);
        Agent third  = new Agent(new UUID(0, 3), AgentType.EXPLORER, Vector3D.ZERO, Vector3D.ZERO);

        world.addAgent(third);
        world.addAgent(first);
        world.addAgent(second);

        assertEquals(List.of(first, second, third), world.agents());
    }
}
