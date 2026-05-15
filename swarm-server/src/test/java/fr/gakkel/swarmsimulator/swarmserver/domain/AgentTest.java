package fr.gakkel.swarmsimulator.swarmserver.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgentTest {

    private static final Vector3D ORIGIN = new Vector3D(0, 0, 0);
    private static final Vector3D SOME_POS = new Vector3D(1, 2, 3);
    private static final Vector3D SOME_VEL = new Vector3D(0.1, 0.2, 0.3);

    @Test
    void create_setsTypeAndInitialPosition_velocityIsZero() {
        // Arrange & Act
        var agent = Agent.create(AgentType.EXPLORER, SOME_POS);

        // Assert
        assertEquals(AgentType.EXPLORER, agent.type());
        assertEquals(SOME_POS, agent.position());
        assertEquals(Vector3D.ZERO, agent.velocity());
    }

    @Test
    void create_eachCallGeneratesUniqueId() {
        var a1 = Agent.create(AgentType.EXPLORER, ORIGIN);
        var a2 = Agent.create(AgentType.EXPLORER, ORIGIN);

        assertNotEquals(a1.id(), a2.id());
    }

    @Test
    void update_changesPositionAndVelocity() {
        // Arrange
        var agent = Agent.create(AgentType.OPERATOR, ORIGIN);

        // Act
        agent.update(SOME_POS, SOME_VEL);

        // Assert
        assertEquals(SOME_POS, agent.position());
        assertEquals(SOME_VEL, agent.velocity());
    }

    @Test
    void equals_sameId_returnsTrue() {
        var id = UUID.randomUUID();
        var a1 = new Agent(id, AgentType.EXPLORER, ORIGIN, Vector3D.ZERO);
        var a2 = new Agent(id, AgentType.OPERATOR, SOME_POS, SOME_VEL);

        assertEquals(a1, a2);
    }

    @Test
    void equals_differentId_returnsFalse() {
        var a1 = Agent.create(AgentType.EXPLORER, ORIGIN);
        var a2 = Agent.create(AgentType.EXPLORER, ORIGIN);

        assertNotEquals(a1, a2);
    }

    @Test
    void hashCode_sameId_returnsSameValue() {
        var id = UUID.randomUUID();
        var a1 = new Agent(id, AgentType.CARRIER, ORIGIN, Vector3D.ZERO);
        var a2 = new Agent(id, AgentType.CARRIER, ORIGIN, Vector3D.ZERO);

        assertEquals(a1.hashCode(), a2.hashCode());
    }

    @Test
    void constructor_nullId_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new Agent(null, AgentType.EXPLORER, ORIGIN, Vector3D.ZERO));
    }

    @Test
    void constructor_nullType_throwsNullPointerException() {
        var id = UUID.randomUUID();
        assertThrows(NullPointerException.class,
                () -> new Agent(id, null, ORIGIN, Vector3D.ZERO));
    }

    @Test
    void update_nullPosition_throwsNullPointerException() {
        var agent = Agent.create(AgentType.EXPLORER, ORIGIN);

        assertThrows(NullPointerException.class, () -> agent.update(null, Vector3D.ZERO));
    }

    @Test
    void update_nullVelocity_throwsNullPointerException() {
        var agent = Agent.create(AgentType.EXPLORER, ORIGIN);

        assertThrows(NullPointerException.class, () -> agent.update(ORIGIN, null));
    }

    @Test
    void carrier_type_isRecognized() {
        var carrier = Agent.create(AgentType.CARRIER, ORIGIN);

        assertEquals(AgentType.CARRIER, carrier.type());
    }
}
