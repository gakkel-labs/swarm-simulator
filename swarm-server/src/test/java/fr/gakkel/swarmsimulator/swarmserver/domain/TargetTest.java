package fr.gakkel.swarmsimulator.swarmserver.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TargetTest {

    @Test
    void constructor_storesPosition() {
        Vector3D position = new Vector3D(10, -20, 5);

        Target target = new Target(position, new SimulationClock());

        assertEquals(position, target.position());
    }

    @Test
    void constructor_nullPosition_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new Target(null, new SimulationClock()));
    }

    @Test
    void isFound_initiallyFalse() {
        Target target = new Target(new Vector3D(0, 0, 0), new SimulationClock());

        assertFalse(target.isFound());
    }

    @Test
    void markFound_setsFoundTrueAndStoresAgentId() {
        Target target = new Target(new Vector3D(0, 0, 0), new SimulationClock());

        target.markFound("agent-42");

        assertTrue(target.isFound());
        assertEquals("agent-42", target.foundByAgentId());
    }

    @Test
    void markFound_idempotent_secondCallIgnored() {
        Target target = new Target(new Vector3D(0, 0, 0), new SimulationClock());
        target.markFound("agent-first");
        target.markFound("agent-second");

        assertEquals("agent-first", target.foundByAgentId());
    }

    @Test
    void constructor_nullClock_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new Target(new Vector3D(0, 0, 0), null));
    }

    @Test
    void elapsedSeconds_countsSimulationTimeSinceTheTargetWasPlaced() {
        SimulationClock clock = new SimulationClock();
        clock.advance(2.0);
        Target target = new Target(new Vector3D(0, 0, 0), clock);

        clock.advance(3.0);

        assertEquals(3.0, target.elapsedSeconds());
    }

    @Test
    void markFound_recordsTheSimulationTimeOfTheDiscovery() {
        SimulationClock clock = new SimulationClock();
        clock.advance(1.0);
        Target target = new Target(new Vector3D(0, 0, 0), clock);
        clock.advance(4.5);

        target.markFound("agent-42");
        clock.advance(10.0);

        assertEquals(4.5, target.foundAtElapsedS());
    }
}
