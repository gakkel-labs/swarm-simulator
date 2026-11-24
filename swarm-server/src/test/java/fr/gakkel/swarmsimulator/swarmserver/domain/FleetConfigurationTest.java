package fr.gakkel.swarmsimulator.swarmserver.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FleetConfigurationTest {

    private static final SearchPatternSettings BOIDS =
            new SearchPatternSettings.Boids(BoidsConfig.builder().build());

    @Test
    void equals_sameValues_returnsTrue() {
        // A value object: defined only by its values, like two 10-euro notes.
        FleetConfiguration first = new FleetConfiguration(20, BOIDS);
        FleetConfiguration second = new FleetConfiguration(20, BOIDS);

        assertEquals(first, second);
    }

    @Test
    void constructor_noDrone_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new FleetConfiguration(0, BOIDS));
    }

    @Test
    void constructor_negativeDroneCount_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new FleetConfiguration(-1, BOIDS));
    }

    @Test
    void constructor_noSearchPattern_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new FleetConfiguration(20, null));
    }

    @Test
    void boidsConstructor_noConfig_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new SearchPatternSettings.Boids(null));
    }
}
