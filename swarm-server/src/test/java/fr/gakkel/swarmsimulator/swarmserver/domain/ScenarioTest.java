package fr.gakkel.swarmsimulator.swarmserver.domain;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioTest {

    @Test
    void withWreckAt_keepsTheWreckPositionAndTheSeed() {
        Vector3D wreckPosition = new Vector3D(10, -20, 5);

        Scenario scenario = Scenario.withWreckAt(wreckPosition, 42L);

        assertEquals(Optional.of(wreckPosition), scenario.wreckPosition());
        assertEquals(42L, scenario.seed());
    }

    @Test
    void withoutWreck_hasNoWreckPosition() {
        Scenario scenario = Scenario.withoutWreck(7L);

        assertTrue(scenario.wreckPosition().isEmpty());
        assertEquals(7L, scenario.seed());
    }

    @Test
    void sameWreckAndSameSeed_areTheSameScenario() {
        // A value object: no identity, two scenarios with the same values are equal.
        assertEquals(Scenario.withWreckAt(new Vector3D(1, -2, 3), 5L),
                Scenario.withWreckAt(new Vector3D(1, -2, 3), 5L));
    }

    @Test
    void nullWreckPosition_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new Scenario(null, 1L));
    }
}
