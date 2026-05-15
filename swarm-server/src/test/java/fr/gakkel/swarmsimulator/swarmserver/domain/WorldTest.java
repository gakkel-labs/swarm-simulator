package fr.gakkel.swarmsimulator.swarmserver.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        var agent = Agent.create(AgentType.OPERATOR, new Vector3D(5, 5, 5));
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
        assertTrue(world.isInBounds(new Vector3D(50, 50, 25)));
    }

    @Test
    void isInBounds_positionOnBoundary_returnsTrue() {
        assertTrue(world.isInBounds(new Vector3D(0, 0, 0)));
        assertTrue(world.isInBounds(new Vector3D(100, 100, 50)));
    }

    @Test
    void isInBounds_positionOutsideWorld_returnsFalse() {
        assertFalse(world.isInBounds(new Vector3D(-1, 50, 25)));
        assertFalse(world.isInBounds(new Vector3D(50, 101, 25)));
        assertFalse(world.isInBounds(new Vector3D(50, 50, 51)));
    }

    @Test
    void clamp_positionOutsideWorld_clampsToBoundary() {
        assertEquals(new Vector3D(0, 0, 0), world.clamp(new Vector3D(-10, -5, -1)));
        assertEquals(new Vector3D(100, 100, 50), world.clamp(new Vector3D(200, 150, 99)));
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
}
