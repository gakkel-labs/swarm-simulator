package fr.gakkel.swarmsimulator.swarmserver.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgentTest {

    private static final Vector3D ORIGIN = new Vector3D(0, 0, 0);
    private static final Vector3D SOME_POS = new Vector3D(1, 2, 3);
    private static final Vector3D SOME_VEL = new Vector3D(0.1, 0.2, 0.3);

    @Test
    void create_setsSpecificationAndInitialPosition_velocityIsZero() {
        // Arrange & Act
        var agent = Agent.create(TestDrones.DEMO_DETECTION_DRONE, SOME_POS);

        // Assert
        assertEquals(TestDrones.DEMO_DETECTION_DRONE, agent.specification());
        assertEquals(SOME_POS, agent.position());
        assertEquals(Vector3D.ZERO, agent.velocity());
    }

    @Test
    void create_eachCallGeneratesUniqueId() {
        var a1 = Agent.create(TestDrones.DEMO_DETECTION_DRONE, ORIGIN);
        var a2 = Agent.create(TestDrones.DEMO_DETECTION_DRONE, ORIGIN);

        assertNotEquals(a1.id(), a2.id());
    }

    @Test
    void update_changesPositionAndVelocity() {
        // Arrange
        var agent = Agent.create(TestDrones.DEMO_DETECTION_DRONE, ORIGIN);

        // Act
        agent.update(SOME_POS, SOME_VEL);

        // Assert
        assertEquals(SOME_POS, agent.position());
        assertEquals(SOME_VEL, agent.velocity());
    }

    @Test
    void equals_sameId_returnsTrue() {
        var id = UUID.randomUUID();
        var a1 = new Agent(id, TestDrones.DEMO_DETECTION_DRONE, ORIGIN, Vector3D.ZERO);
        var a2 = new Agent(id, TestDrones.batchDetectionDrone(), SOME_POS, SOME_VEL);

        assertEquals(a1, a2);
    }

    @Test
    void equals_differentId_returnsFalse() {
        var a1 = Agent.create(TestDrones.DEMO_DETECTION_DRONE, ORIGIN);
        var a2 = Agent.create(TestDrones.DEMO_DETECTION_DRONE, ORIGIN);

        assertNotEquals(a1, a2);
    }

    @Test
    void hashCode_sameId_returnsSameValue() {
        var id = UUID.randomUUID();
        var a1 = new Agent(id, TestDrones.DEMO_DETECTION_DRONE, ORIGIN, Vector3D.ZERO);
        var a2 = new Agent(id, TestDrones.DEMO_DETECTION_DRONE, ORIGIN, Vector3D.ZERO);

        assertEquals(a1.hashCode(), a2.hashCode());
    }

    @Test
    void constructor_nullId_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new Agent(null, TestDrones.DEMO_DETECTION_DRONE, ORIGIN, Vector3D.ZERO));
    }

    @Test
    void type_detectionDrone_comesFromTheSpecification() {
        var agent = Agent.create(TestDrones.DEMO_DETECTION_DRONE, ORIGIN);

        assertEquals(AgentType.EXPLORER, agent.type());
    }

    @Test
    void constructor_nullSpecification_throwsNullPointerException() {
        var id = UUID.randomUUID();
        assertThrows(NullPointerException.class,
                () -> new Agent(id, null, ORIGIN, Vector3D.ZERO));
    }

    @Test
    void update_nullPosition_throwsNullPointerException() {
        var agent = Agent.create(TestDrones.DEMO_DETECTION_DRONE, ORIGIN);

        assertThrows(NullPointerException.class, () -> agent.update(null, Vector3D.ZERO));
    }

    @Test
    void update_nullVelocity_throwsNullPointerException() {
        var agent = Agent.create(TestDrones.DEMO_DETECTION_DRONE, ORIGIN);

        assertThrows(NullPointerException.class, () -> agent.update(ORIGIN, null));
    }


    @Test
    void update_motionGivesPositionAndVelocityOfTheSameStep() {
        // Another thread (the broadcaster) must never read a position from one step and a
        // velocity from another: both are kept in one Motion value.
        var agent = Agent.create(TestDrones.DEMO_DETECTION_DRONE, ORIGIN);

        agent.update(SOME_POS, SOME_VEL);
        Motion motion = agent.motion();

        assertEquals(SOME_POS, motion.position());
        assertEquals(SOME_VEL, motion.velocity());
    }
}
