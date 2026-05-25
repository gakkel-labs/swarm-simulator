package fr.gakkel.swarmsimulator.swarmserver.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TargetTest {

    @Test
    void constructor_storesPosition() {
        Vector3D position = new Vector3D(10, -20, 5);

        Target target = new Target(position);

        assertEquals(position, target.position());
    }

    @Test
    void constructor_nullPosition_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new Target(null));
    }

    @Test
    void isFound_initiallyFalse() {
        Target target = new Target(new Vector3D(0, 0, 0));

        assertFalse(target.isFound());
    }

    @Test
    void markFound_setsFoundTrueAndStoresAgentId() {
        Target target = new Target(new Vector3D(0, 0, 0));

        target.markFound("agent-42");

        assertTrue(target.isFound());
        assertEquals("agent-42", target.foundByAgentId());
    }

    @Test
    void markFound_idempotent_secondCallIgnored() {
        Target target = new Target(new Vector3D(0, 0, 0));
        target.markFound("agent-first");
        target.markFound("agent-second");

        assertEquals("agent-first", target.foundByAgentId());
    }

    @Test
    void elapsedSeconds_isPositive() {
        Target target = new Target(new Vector3D(0, 0, 0));

        double elapsed = target.elapsedSeconds();

        assertTrue(elapsed >= 0.0);
    }

    @Test
    void markFound_recordsElapsedAtFoundTime() {
        Target target = new Target(new Vector3D(0, 0, 0));
        target.markFound("agent-42");

        double foundAt = target.foundAtElapsedS();

        assertTrue(foundAt >= 0.0);
        assertTrue(foundAt <= target.elapsedSeconds() + 0.01);
    }
}
